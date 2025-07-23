package otoroshi.script.plugins

import org.apache.pekko.http.scaladsl.util.FastFuture
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Flow, Source}
import org.apache.pekko.util.ByteString
import com.github.blemale.scaffeine.Scaffeine
import otoroshi.env.Env
import otoroshi.next.models.NgPlugins
import otoroshi.script._
import otoroshi.utils.RegexPool
import play.api.libs.json.{Format, JsArray, JsError, JsObject, JsResult, JsString, JsSuccess, JsValue, Json}
import play.api.mvc.{AnyContent, Request, RequestHeader, Result}
import otoroshi.utils.syntax.implicits._
import otoroshi.utils
import otoroshi.utils.http.RequestImplicits._

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.Try

object Plugins {
  val format: Format[Plugins] = new Format[Plugins] {
    override def writes(o: Plugins): JsValue             =
      Json.obj(
        "enabled"  -> o.enabled,
        "refs"     -> JsArray(o.refs.map(JsString.apply)),
        "config"   -> o.config,
        "excluded" -> JsArray(o.excluded.map(JsString.apply))
      )
    override def reads(json: JsValue): JsResult[Plugins] =
      Try {
        JsSuccess(
          Plugins(
            refs = (json \ "refs")
              .asOpt[Seq[String]]
              .getOrElse(Seq.empty),
            enabled = (json \ "enabled").asOpt[Boolean].getOrElse(false),
            config = (json \ "config").asOpt[JsValue].getOrElse(Json.obj()),
            excluded = (json \ "excluded").asOpt[Seq[String]].getOrElse(Seq.empty[String])
          )
        )
      } recover { case e =>
        JsError(e.getMessage)
      } get
  }
}

case class Plugins(
    enabled: Boolean = false,
    excluded: Seq[String] = Seq.empty[String],
    refs: Seq[String] = Seq.empty,
    config: JsValue = Json.obj()
) {

  private val transformers = new AtomicReference[Seq[String]](null)

  private def plugin[A](ref: String)(implicit ec: ExecutionContext, env: Env, ct: ClassTag[A]): Option[A] = {
    env.scriptManager.getAnyScript[NamedPlugin](ref) match {
      case Right(validator) if ct.runtimeClass.isAssignableFrom(validator.getClass) => validator.asInstanceOf[A].some
      case _                                                                        => None
    }
  }

  private def filterPatternExclusionPerPlugin(pls: Plugins, req: RequestHeader): Plugins = {
    if (pls.excluded.exists(_.startsWith("only:"))) {
      val badRefs = pls.excluded
        .filter(_.startsWith("only:"))
        .map(_.replace("only:", ""))
        .map {
          case excl if excl.startsWith("cp:") =>
              val parts = excl.replace("cp:", "").split(":")
              ("cp:" + parts(0), parts(1))
          case excl                           =>
              val parts = excl.split(":")
              (parts(0), parts(1))
        }
        .filter { case (_, pattern) =>
          utils.RegexPool.regex(pattern).matches(req.thePath)
        }
        .map { case (plugin, _) =>
          plugin
        }
        .distinct
      val refs    = pls.refs.diff(badRefs)
      pls.copy(refs = refs)
    } else {
      pls
    }
  }

  private def getPlugins[A](
      req: RequestHeader
  )(implicit ec: ExecutionContext, env: Env, ct: ClassTag[A]): Seq[String] = {
    val globalPlugins = env.datastores.globalConfigDataStore.latestSafe
      .map(_.plugins)
      .filter(p => p.enabled && p.refs.nonEmpty)
      .filter(pls =>
        pls.excluded.isEmpty || !pls.excluded
          .filterNot(_.startsWith("only:"))
          .exists(p => utils.RegexPool.regex(p).matches(req.thePath))
      )
      .map(pls => filterPatternExclusionPerPlugin(pls, req))
      .getOrElse(Plugins())
      .refs
      .map(r => (r, plugin[A](r)))
      .collect { case (ref, Some(_)) =>
        ref
      }
    val localPlugins  = Some(this)
      .filter(p => p.enabled && p.refs.nonEmpty)
      .filter(pls =>
        pls.excluded.isEmpty || !pls.excluded
          .filterNot(_.startsWith("only:"))
          .exists(p => RegexPool.regex(p).matches(req.thePath))
      )
      .map(pls => filterPatternExclusionPerPlugin(pls, req))
      .getOrElse(Plugins())
      .refs
      .map(r => (r, plugin[A](r)))
      .collect { case (ref, Some(_)) =>
        ref
      }
    (globalPlugins ++ localPlugins).distinct
  }

  def json: JsValue = Plugins.format.writes(this)

  def sinks(req: RequestHeader)(implicit ec: ExecutionContext, env: Env): Seq[String] = {
    getPlugins[RequestSink](req)
  }

  def preRoutings(req: RequestHeader)(implicit ec: ExecutionContext, env: Env): Seq[String] = {
    getPlugins[PreRouting](req)
  }

  def accessValidators(req: RequestHeader)(implicit ec: ExecutionContext, env: Env): Seq[String] = {
    getPlugins[AccessValidator](req)
  }

  def requestTransformers(req: RequestHeader)(implicit ec: ExecutionContext, env: Env): Seq[String] = {
    val cachedTransformers = transformers.get()
    if (cachedTransformers == null) {
      val trs = getPlugins[RequestTransformer](req)
      transformers.compareAndSet(null, trs)
      trs
    } else {
      cachedTransformers
    }
  }

  private val request_handlers_cache      =
    Scaffeine().maximumSize(2).expireAfterWrite(1.minute).build[String, (Boolean, Map[String, RequestHandler])]()
  private val request_handlers_cache_name = "singleton"
  // private val request_handlers_ref = new AtomicReference[(Boolean, Map[String, RequestHandler])]()

  private def getHandlersMap(
      request: RequestHeader
  )(implicit ec: ExecutionContext, env: Env): (Boolean, Map[String, RequestHandler]) =
    env.metrics.withTimer("otoroshi.plugins.req-handlers.handlers-map-compute") {
      request_handlers_cache.get(
        request_handlers_cache_name,
        _ => {
          val handlers    = getPlugins[RequestHandler](request)
          val handlersMap =
            handlers.flatMap(h => plugin[RequestHandler](h)).flatMap(rh => rh.handledDomains.map(d => (d, rh))).toMap
          val hasWildcard = handlersMap.keys.exists(_.contains("*"))
          (hasWildcard, handlersMap)
        }
      )
      // request_handlers_ref.getOrSet {
      //   val handlers = getPlugins[RequestHandler](request)
      //   val handlersMap = handlers.flatMap(h => plugin[RequestHandler](h)).flatMap(rh => rh.handledDomains.map(d => (d, rh))).toMap
      //   val hasWildcard = handlersMap.keys.exists(_.contains("*"))
      //   (hasWildcard, handlersMap)
      // }
    }

  def canHandleRequest(request: RequestHeader)(implicit ec: ExecutionContext, env: Env): Boolean =
    env.metrics.withTimer("otoroshi.plugins.req-handlers.can-handle-request") {
      if (enabled) {
        val (handlersMapHasWildcard, handlersMap) = getHandlersMap(request)
        if (handlersMap.nonEmpty) {
          if (handlersMapHasWildcard) {
            if (handlersMap.contains("*")) {
              true
            } else {
              handlersMap.exists { case (key, _) =>
                RegexPool(key).matches(request.theDomain)
              }
            }
          } else {
            handlersMap.contains(request.theDomain)
          }
        } else {
          false
        }
      } else {
        false
      }
    }

  def handleRequest(
      request: Request[Source[ByteString, _]],
      defaultRouting: Request[Source[ByteString, _]] => Future[Result]
  )(implicit ec: ExecutionContext, env: Env): Future[Result] = env.metrics.withTimer("handle-ng-dispatch") {
    if (enabled) {
      val (handlersMapHasWildcard, handlersMap) = getHandlersMap(request)
      val maybeHandler                          =
        if (handlersMapHasWildcard) handlersMap.find(t => RegexPool(t._1).matches(request.theDomain)).map(_._2)
        else handlersMap.get(request.theDomain)
      maybeHandler match {
        case None          => defaultRouting(request)
        case Some(handler) => env.metrics.withTimerAsync("handle-ng-request")(handler.handle(request, defaultRouting))
      }
    } else {
      defaultRouting(request)
    }
  }

  def handleWsRequest(
      request: RequestHeader,
      defaultRouting: RequestHeader => Future[
        Either[Result, Flow[play.api.http.websocket.Message, play.api.http.websocket.Message, _]]
      ]
  )(implicit
      ec: ExecutionContext,
      env: Env
  ): Future[Either[Result, Flow[play.api.http.websocket.Message, play.api.http.websocket.Message, _]]] =
    env.metrics.withTimer("handle-ng-ws-dispatch") {
      if (enabled) {
        val (handlersMapHasWildcard, handlersMap) = getHandlersMap(request)
        val maybeHandler                          =
          if (handlersMapHasWildcard) handlersMap.find(t => RegexPool(t._1).matches(request.theDomain)).map(_._2)
          else handlersMap.get(request.theDomain)
        maybeHandler match {
          case None          => defaultRouting(request)
          case Some(handler) =>
            env.metrics.withTimerAsync("handle-ng-ws-request")(handler.handleWs(request, defaultRouting))
        }
      } else {
        defaultRouting(request)
      }
    }

  def ngPlugins(): NgPlugins = NgPlugins.readFrom(config.select("ng"))
}
