package otoroshi.plugins.cache

import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import org.apache.pekko.actor.{ActorSystem, Cancellable}
import org.apache.pekko.http.scaladsl.util.FastFuture
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import otoroshi.env.Env
import otoroshi.next.plugins.api.{NgPluginCategory, NgPluginVisibility, NgStep}
import otoroshi.script.{HttpRequest, RequestTransformer, TransformerRequestContext, TransformerResponseBodyContext}
import otoroshi.utils.{RegexPool, SchedulerHelper}
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{RequestHeader, Result, Results}
import redis.{RedisClientMasterSlaves, RedisServer}
import otoroshi.utils.http.RequestImplicits._
import otoroshi.utils.syntax.implicits.{BetterJsValue, BetterSyntax}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

case class ResponseCacheFilterConfig(json: JsValue) {
  lazy val statuses: Seq[Int]      = (json \ "statuses")
    .asOpt[Seq[Int]]
    .orElse((json \ "statuses").asOpt[Seq[String]].map(_.map(_.toInt)))
    .getOrElse(Seq(200))
  lazy val methods: Seq[String]    = (json \ "methods").asOpt[Seq[String]].getOrElse(Seq("GET"))
  lazy val paths: Seq[String]      = (json \ "paths").asOpt[Seq[String]].getOrElse(Seq("/.*"))
  lazy val notStatuses: Seq[Int]   = (json \ "not" \ "statuses")
    .asOpt[Seq[Int]]
    .orElse((json \ "not" \ "statuses").asOpt[Seq[String]].map(_.map(_.toInt)))
    .getOrElse(Seq.empty)
  lazy val notMethods: Seq[String] = (json \ "not" \ "methods").asOpt[Seq[String]].getOrElse(Seq.empty)
  lazy val notPaths: Seq[String]   = (json \ "not" \ "paths").asOpt[Seq[String]].getOrElse(Seq.empty)
}

case class ResponseCacheConfig(json: JsValue) {
  lazy val enabled: Boolean                          = (json \ "enabled").asOpt[Boolean].getOrElse(true)
  lazy val ttl: Long                                 = (json \ "ttl").asOpt[Long].getOrElse(60.minutes.toMillis)
  lazy val filter: Option[ResponseCacheFilterConfig] =
    (json \ "filter").asOpt[JsObject].map(o => ResponseCacheFilterConfig(o))
  lazy val hasFilter: Boolean                        = filter.isDefined
  lazy val maxSize: Long                             = (json \ "maxSize").asOpt[Long].getOrElse(50L * 1024L * 1024L)
  lazy val autoClean: Boolean                        = (json \ "autoClean").asOpt[Boolean].getOrElse(true)
}

object ResponseCache {
  val base64Encoder = java.util.Base64.getEncoder
  val base64Decoder = java.util.Base64.getDecoder
  val logger: Logger        = Logger("otoroshi-plugins-response-cache")
}

// MIGRATED
class ResponseCache extends RequestTransformer {

  override def name: String = "Response Cache"

  override def defaultConfig: Option[JsObject] =
    Some(
      Json.obj(
        "ResponseCache" -> Json.obj(
          "enabled"   -> true,
          "ttl"       -> 60.minutes.toMillis,
          "maxSize"   -> 50L * 1024L * 1024L,
          "autoClean" -> true,
          "filter"    -> Json.obj(
            "statuses" -> Json.arr(),
            "methods"  -> Json.arr(),
            "paths"    -> Json.arr(),
            "not"      -> Json.obj(
              "statuses" -> Json.arr(),
              "methods"  -> Json.arr(),
              "paths"    -> Json.arr()
            )
          )
        )
      )
    )

  override def description: Option[String] =
    Some("""This plugin can cache responses from target services in the otoroshi datasstore
      |It also provides a debug UI at `/.well-known/otoroshi/bodylogger`.
      |
      |This plugin can accept the following configuration
      |
      |```json
      |{
      |  "ResponseCache": {
      |    "enabled": true, // enabled cache
      |    "ttl": 300000,  // store it for some times (5 minutes by default)
      |    "maxSize": 5242880, // max body size (body will be cut after that)
      |    "autoClean": true, // cleanup older keys when all bigger than maxSize
      |    "filter": { // cache only for some status, method and paths
      |      "statuses": [],
      |      "methods": [],
      |      "paths": [],
      |      "not": {
      |        "statuses": [],
      |        "methods": [],
      |        "paths": []
      |      }
      |    }
      |  }
      |}
      |```
    """.stripMargin)

  override def visibility: NgPluginVisibility    = NgPluginVisibility.NgUserLand
  override def categories: Seq[NgPluginCategory] = Seq(NgPluginCategory.Other)
  override def steps: Seq[NgStep]                = Seq(NgStep.TransformRequest, NgStep.TransformResponse)

  private val ref    = new AtomicReference[(RedisClientMasterSlaves, ActorSystem)]()
  private val jobRef = new AtomicReference[Cancellable]()

  override def start(env: Env): Future[Unit] = {
    val actorSystem = ActorSystem("cache-redis")
    implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher
    jobRef.set(env.otoroshiScheduler.scheduleAtFixedRate(1.minute, 10.minutes) {
      //jobRef.set(env.otoroshiScheduler.scheduleAtFixedRate(10.seconds, 10.seconds) {
      SchedulerHelper.runnable(
        try {
          cleanCache(env)
        } catch {
          case e: Throwable =>
            ResponseCache.logger.error("error while cleaning cache", e)
        }
      )
    })
    env.datastores.globalConfigDataStore.singleton()(ec, env).map { conf =>
      if ((conf.scripts.transformersConfig \ "ResponseCache").isDefined) {
        val redis: RedisClientMasterSlaves = {
          val master = RedisServer(
            host = (conf.scripts.transformersConfig \ "ResponseCache" \ "redis" \ "host")
              .asOpt[String]
              .getOrElse("localhost"),
            port = (conf.scripts.transformersConfig \ "ResponseCache" \ "redis" \ "port").asOpt[Int].getOrElse(6379),
            password = (conf.scripts.transformersConfig \ "ResponseCache" \ "redis" \ "password").asOpt[String]
          )
          val slaves = (conf.scripts.transformersConfig \ "ResponseCache" \ "redis" \ "slaves")
            .asOpt[Seq[JsObject]]
            .getOrElse(Seq.empty)
            .map { config =>
              RedisServer(
                host = (config \ "host").asOpt[String].getOrElse("localhost"),
                port = (config \ "port").asOpt[Int].getOrElse(6379),
                password = (config \ "password").asOpt[String]
              )
            }
          RedisClientMasterSlaves(master, slaves)(actorSystem)
        }
        ref.set((redis, actorSystem))
      }
      ()
    }
  }

  override def stop(env: Env): Future[Unit] = {
    Option(ref.get()).foreach(_._2.terminate())
    Option(jobRef.get()).foreach(_.cancel())
    FastFuture.successful(())
  }

  private def cleanCache(env: Env): Future[Unit] = {
    implicit val ev: Env = env
    implicit val ec: ExecutionContext = env.otoroshiExecutionContext
    implicit val mat: Materializer = env.otoroshiMaterializer
    env.datastores.serviceDescriptorDataStore.findAll().flatMap { services =>
      val possibleServices = services.filter(s =>
        s.transformerRefs.nonEmpty && s.transformerRefs.contains("cp:otoroshi.plugins.cache.ResponseCache")
      )
      val functions        = possibleServices.map { service => () =>
        {
          val config  =
            ResponseCacheConfig(service.transformerConfig.select("ResponseCache").asOpt[JsObject].getOrElse(Json.obj()))
          val maxSize = config.maxSize
          if (config.autoClean) {
            env.datastores.rawDataStore.keys(s"${env.storageRoot}:noclustersync:cache:${service.id}:*").flatMap {
              keys =>
                if (keys.nonEmpty) {
                  Source(keys.toList)
                    .mapAsync(1) { key =>
                      for {
                        size <- env.datastores.rawDataStore.strlen(key).map(_.getOrElse(0L))
                        pttl <- env.datastores.rawDataStore.pttl(key)
                      } yield (key, size, pttl)
                    }
                    .runWith(Sink.seq)
                    .flatMap { values =>
                      val globalSize = values.foldLeft(0L)((a, b) => a + b._2)
                      if (globalSize > maxSize) {
                        var acc    = 0L
                        val sorted = values
                          .sortWith((a, b) => a._3.compareTo(b._3) < 0)
                          .filter { t =>
                            if ((globalSize - acc) < maxSize) {
                              acc = acc + t._2
                              false
                            } else {
                              acc = acc + t._2
                              true
                            }
                          }
                          .map(_._1)
                        env.datastores.rawDataStore.del(sorted).map(_ => ())
                      } else {
                        ().future
                      }
                    }
                } else {
                  ().future
                }
            }
          } else {
            ().future
          }
        }
      }
      Source(functions.toList)
        .mapAsync(1) { f => f() }
        .runWith(Sink.ignore)
        .map(_ => ())
    }
  }

  private def get(key: String)(implicit env: Env, ec: ExecutionContext): Future[Option[ByteString]] = {
    ref.get() match {
      case null  => env.datastores.rawDataStore.get(key)
      case redis => redis._1.get(key)
    }
  }

  private def set(key: String, value: ByteString, ttl: Option[Long])(implicit
      ec: ExecutionContext,
      env: Env
  ): Future[Boolean] = {
    ref.get() match {
      case null  => env.datastores.rawDataStore.set(key, value, ttl)
      case redis => redis._1.set(key, value, pxMilliseconds = ttl)
    }
  }

  private def filter(req: RequestHeader, config: ResponseCacheConfig, statusOpt: Option[Int] = None): Boolean = {
    config.filter match {
      case None         => true
      case Some(filter) =>
        val matchPath      =
          if (filter.paths.isEmpty) true else filter.paths.exists(p => RegexPool.regex(p).matches(req.relativeUri))
        val matchNotPath   =
          if (filter.notPaths.isEmpty) false
          else filter.notPaths.exists(p => RegexPool.regex(p).matches(req.relativeUri))
        val methodMatch    =
          if (filter.methods.isEmpty) true else filter.methods.map(_.toLowerCase()).contains(req.method.toLowerCase())
        val methodNotMatch =
          if (filter.notMethods.isEmpty) false
          else filter.notMethods.map(_.toLowerCase()).contains(req.method.toLowerCase())
        val statusMatch    =
          if (filter.statuses.isEmpty) true
          else
            statusOpt match {
              case None         => true
              case Some(status) => filter.statuses.contains(status)
            }
        val statusNotMatch =
          if (filter.notStatuses.isEmpty) false
          else
            statusOpt match {
              case None         => true
              case Some(status) => filter.notStatuses.contains(status)
            }
        matchPath && methodMatch && statusMatch && !matchNotPath && !methodNotMatch && !statusNotMatch
    }
  }

  private def couldCacheResponse(ctx: TransformerResponseBodyContext, config: ResponseCacheConfig): Boolean = {
    if (filter(ctx.request, config, Some(ctx.rawResponse.status))) {
      ctx.rawResponse.headers
        .get("Content-Length")
        .orElse(ctx.rawResponse.headers.get("content-Length"))
        .map(_.toInt) match {
        case Some(csize) if csize <= config.maxSize => true
        case Some(csize) if csize > config.maxSize  => false
        case _                                      => true
      }
    } else {
      false
    }
  }

  private def cachedResponse(
      ctx: TransformerRequestContext,
      config: ResponseCacheConfig
  )(implicit env: Env, ec: ExecutionContext, mat: Materializer): Future[Either[Unit, Option[JsValue]]] = {
    if (filter(ctx.request, config)) {
      get(
        s"${env.storageRoot}:noclustersync:cache:${ctx.descriptor.id}:${ctx.request.method.toLowerCase()}-${ctx.request.relativeUri}"
      ).map {
        case None       => Right(None)
        case Some(json) => Right(Some(Json.parse(json.utf8String)))
      }
    } else {
      FastFuture.successful(Left(()))
    }
  }

  override def transformRequestWithCtx(
      ctx: TransformerRequestContext
  )(implicit env: Env, ec: ExecutionContext, mat: Materializer): Future[Either[Result, HttpRequest]] = {
    val config = ResponseCacheConfig(ctx.configFor("ResponseCache"))
    if (config.enabled) {
      cachedResponse(ctx, config).map {
        case Left(_)          => Right(ctx.otoroshiRequest)
        case Right(None)      =>
          Right(
            ctx.otoroshiRequest.copy(
              headers = ctx.otoroshiRequest.headers ++ Map("X-Otoroshi-Cache" -> "MISS")
            )
          )
        case Right(Some(res)) =>
          val status  = (res \ "status").as[Int]
          val body    = ByteString(ResponseCache.base64Decoder.decode((res \ "body").as[String]))
          val headers = (res \ "headers").as[Map[String, String]] ++ Map("X-Otoroshi-Cache" -> "HIT")
          val ctype   = (res \ "ctype").as[String]
          if (ResponseCache.logger.isDebugEnabled)
            ResponseCache.logger.debug(
              s"Serving '${ctx.request.method.toLowerCase()} - ${ctx.request.relativeUri}' from cache"
            )
          Left(Results.Status(status)(body).as(ctype).withHeaders(headers.toSeq: _*))
      }
    } else {
      FastFuture.successful(Right(ctx.otoroshiRequest))
    }
  }

  override def transformResponseBodyWithCtx(
      ctx: TransformerResponseBodyContext
  )(implicit env: Env, ec: ExecutionContext, mat: Materializer): Source[ByteString, _] = {
    val config = ResponseCacheConfig(ctx.configFor("ResponseCache"))
    if (config.enabled && couldCacheResponse(ctx, config)) {
      val size = new AtomicLong(0L)
      val ref  = new AtomicReference[ByteString](ByteString.empty)
      ctx.body
        .wireTap(bs =>
          ref.updateAndGet { (t: ByteString) =>
            val currentSize = size.addAndGet(bs.size.toLong)
            if (currentSize <= config.maxSize) {
              t ++ bs
            } else {
              t
            }
          }
        )
        .alsoTo(Sink.onComplete {
          case _ =>
            if (size.get() < config.maxSize) {
              val ctype: String                = ctx.rawResponse.headers
                .get("Content-Type")
                .orElse(ctx.rawResponse.headers.get("content-type"))
                .getOrElse("text/plain")
              val headers: Map[String, String] = ctx.rawResponse.headers.filterNot { tuple =>
                val name = tuple._1.toLowerCase()
                name == "content-type" || name == "transfer-encoding" || name == "content-length"
              }
              val event                        = Json.obj(
                "status"  -> ctx.rawResponse.status,
                "headers" -> headers,
                "ctype"   -> ctype,
                "body"    -> ResponseCache.base64Encoder.encodeToString(ref.get().toArray)
              )
              if (ResponseCache.logger.isDebugEnabled)
                ResponseCache.logger.debug(
                  s"Storing '${ctx.request.method.toLowerCase()} - ${ctx.request.relativeUri}' in cache for the next ${config.ttl} ms."
                )
              set(
                s"${env.storageRoot}:noclustersync:cache:${ctx.descriptor.id}:${ctx.request.method.toLowerCase()}-${ctx.request.relativeUri}",
                ByteString(Json.stringify(event)),
                Some(config.ttl)
              )
            }
        })
    } else {
      ctx.body
    }
  }
}
