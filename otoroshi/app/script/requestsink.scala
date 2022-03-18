package otoroshi.script

import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.Source
import akka.util.ByteString
import otoroshi.env.Env
import otoroshi.next.plugins.api.{NgPluginCategory, NgPluginVisibility, NgStep}
import otoroshi.utils.TypedMap
import otoroshi.utils.config.ConfigUtils
import play.api.libs.json._
import play.api.mvc.{RequestHeader, Result, Results}

import scala.concurrent.{ExecutionContext, Future}

trait RequestSink extends StartableAndStoppable with NamedPlugin with InternalEventListener {
  def pluginType: PluginType                                                                       = PluginType.RequestSinkType
  def matches(context: RequestSinkContext)(implicit env: Env, ec: ExecutionContext): Boolean       = false
  def handle(context: RequestSinkContext)(implicit env: Env, ec: ExecutionContext): Future[Result] =
    FastFuture.successful(Results.NotImplemented(Json.obj("error" -> "not implemented yet")))
}

object RequestSink {

  def maybeSinkRequest(
      snowflake: String,
      req: RequestHeader,
      body: Source[ByteString, _],
      attrs: TypedMap,
      origin: RequestOrigin,
      status: Int,
      message: String,
      err: => Future[Result]
  )(implicit ec: ExecutionContext, env: Env): Future[Result] =
    env.metrics.withTimerAsync("otoroshi.core.proxy.request-sink") {
      env.datastores.globalConfigDataStore.singleton().flatMap {
        case config if !config.scripts.enabled                                        => err
        case config if (config.scripts.sinkRefs ++ config.plugins.sinks(req)).isEmpty => err
        case config                                                                   =>
          val ctx = RequestSinkContext(
            snowflake = snowflake,
            index = -1,
            request = req,
            config = ConfigUtils.merge(config.scripts.sinkConfig, config.plugins.config),
            attrs = attrs,
            status = status,
            message = message,
            origin = origin,
            body = body
          )
          val rss = (config.scripts.sinkRefs ++ config.plugins
            .sinks(req)).distinct.map(r => env.scriptManager.getAnyScript[RequestSink](r)).collect { case Right(rs) =>
            rs
          }
          rss.find(_.matches(ctx)) match {
            case None     => err
            case Some(rs) => rs.handle(ctx)
          }
      }
    }
}

sealed trait RequestOrigin
object RequestOrigin {
  case object ErrorHandler extends RequestOrigin
  case object ReverseProxy extends RequestOrigin
}

case class RequestSinkContext(
    snowflake: String,
    index: Int,
    request: RequestHeader,
    config: JsValue,
    attrs: TypedMap,
    origin: RequestOrigin,
    status: Int,
    message: String,
    body: Source[ByteString, _]
) extends ContextWithConfig {

  private def conf[A](prefix: String = "config-"): Option[JsValue] = {
    config match {
      case json: JsArray  => Option(json.value(index)).orElse((config \ s"$prefix$index").asOpt[JsValue])
      case json: JsObject => (json \ s"$prefix$index").asOpt[JsValue]
      case _              => None
    }
  }
  private def confAt[A](key: String, prefix: String = "config-")(implicit fjs: Reads[A]): Option[A] = {
    val conf = config match {
      case json: JsArray  => Option(json.value(index)).getOrElse((config \ s"$prefix$index").as[JsValue])
      case json: JsObject => (json \ s"$prefix$index").as[JsValue]
      case _              => Json.obj()
    }
    (conf \ key).asOpt[A]
  }

  override def globalConfig: JsValue = config
}

object DefaultRequestSink extends RequestSink {
  def visibility: NgPluginVisibility = NgPluginVisibility.NgInternal
  def categories: Seq[NgPluginCategory] = Seq.empty
  def steps: Seq[NgStep] = Seq.empty
}

object CompilingRequestSink extends RequestSink {
  def visibility: NgPluginVisibility = NgPluginVisibility.NgInternal
  def categories: Seq[NgPluginCategory] = Seq.empty
  def steps: Seq[NgStep] = Seq.empty
}
