package otoroshi.script

import akka.http.scaladsl.util.FastFuture
import akka.util.ByteString
import otoroshi.env.Env
import otoroshi.gateway.GwError
import otoroshi.models.ServiceDescriptor
import otoroshi.next.plugins.api.{NgPluginCategory, NgPluginVisibility, NgStep}
import otoroshi.script.CompilingPreRouting.funit
import otoroshi.utils.TypedMap
import play.api.libs.json._
import play.api.mvc.{RequestHeader, Result, Results}
import play.twirl.api.Html

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NoStackTrace

case class PreRoutingError(
    body: ByteString,
    code: Int = 500,
    contentType: String,
    headers: Map[String, String] = Map.empty
) extends RuntimeException("PreRoutingError")
    with NoStackTrace {
  def asResult: Result = {
    Results.Status(code).apply(body).as(contentType).withHeaders(headers.toSeq: _*)
  }
}
case class PreRoutingErrorWithResult(result: Result)
    extends RuntimeException("PreRoutingErrorWithResult")
    with NoStackTrace

object PreRoutingError {
  def fromString(body: String, code: Int = 500, contentType: String = "text/plain"): PreRoutingError      =
    new PreRoutingError(ByteString(body), code, contentType)
  def fromJson(body: JsValue, code: Int = 500, contentType: String = "application/json"): PreRoutingError =
    new PreRoutingError(ByteString(Json.stringify(body)), code, contentType)
  def fromHtml(body: Html, code: Int = 500, contentType: String = "text/html"): PreRoutingError           =
    new PreRoutingError(ByteString(body.body), code, contentType)
}

case class PreRoutingRef(
    enabled: Boolean = false,
    excludedPatterns: Seq[String] = Seq.empty[String],
    refs: Seq[String] = Seq.empty,
    config: JsValue = Json.obj()
) {
  def json: JsValue = PreRoutingRef.format.writes(this)
}

object PreRoutingRef {
  val format = new Format[PreRoutingRef] {
    override def writes(o: PreRoutingRef): JsValue             =
      Json.obj(
        "enabled"          -> o.enabled,
        "refs"             -> JsArray(o.refs.map(JsString.apply)),
        "config"           -> o.config,
        "excludedPatterns" -> JsArray(o.excludedPatterns.map(JsString.apply))
      )
    override def reads(json: JsValue): JsResult[PreRoutingRef] =
      Try {
        JsSuccess(
          PreRoutingRef(
            refs = (json \ "refs")
              .asOpt[Seq[String]]
              .orElse((json \ "ref").asOpt[String].map(r => Seq(r)))
              .getOrElse(Seq.empty),
            enabled = (json \ "enabled").asOpt[Boolean].getOrElse(false),
            config = (json \ "config").asOpt[JsValue].getOrElse(Json.obj()),
            excludedPatterns = (json \ "excludedPatterns").asOpt[Seq[String]].getOrElse(Seq.empty[String])
          )
        )
      } recover { case e =>
        JsError(e.getMessage)
      } get
  }
}

trait PreRouting extends StartableAndStoppable with NamedPlugin with InternalEventListener {
  def pluginType: PluginType                                                                      = PluginType.PreRoutingType
  def preRoute(context: PreRoutingContext)(implicit env: Env, ec: ExecutionContext): Future[Unit] = funit
}

case class PreRoutingContext(
    snowflake: String,
    index: Int,
    request: RequestHeader,
    descriptor: ServiceDescriptor,
    config: JsValue,
    attrs: TypedMap,
    globalConfig: JsValue
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
}

object DefaultPreRouting extends PreRouting {
  override def preRoute(context: PreRoutingContext)(implicit env: Env, ec: ExecutionContext): Future[Unit] = funit
  def visibility: NgPluginVisibility = NgPluginVisibility.NgInternal
  def categories: Seq[NgPluginCategory] = Seq.empty
  def steps: Seq[NgStep] = Seq.empty
}

object CompilingPreRouting extends PreRouting {
  override def preRoute(context: PreRoutingContext)(implicit env: Env, ec: ExecutionContext): Future[Unit] = funit
  def visibility: NgPluginVisibility = NgPluginVisibility.NgInternal
  def categories: Seq[NgPluginCategory] = Seq.empty
  def steps: Seq[NgStep] = Seq.empty
}

class FailingPreRoute extends PreRouting {
  def visibility: NgPluginVisibility = NgPluginVisibility.NgInternal
  def categories: Seq[NgPluginCategory] = Seq.empty
  def steps: Seq[NgStep] = Seq.empty
  override def preRoute(context: PreRoutingContext)(implicit env: Env, ec: ExecutionContext): Future[Unit] = {
    context.attrs.put(otoroshi.plugins.Keys.GwErrorKey -> GwError("epic fail!"))
    Future.failed(PreRoutingError.fromString("epic fail!"))
  }
}
