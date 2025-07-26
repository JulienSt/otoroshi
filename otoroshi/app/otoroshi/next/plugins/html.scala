package otoroshi.next.plugins

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import otoroshi.el.GlobalExpressionLanguage
import otoroshi.env.Env
import otoroshi.next.plugins.api._
import otoroshi.utils.gzip.GzipFlow
import otoroshi.utils.syntax.implicits._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result

import scala.concurrent.{ExecutionContext, Future}

case class NgHtmlPatcherConfig(
    appendHead: Seq[String] = Seq.empty,
    appendBody: Seq[String] = Seq.empty,
    prependHead: Seq[String] = Seq.empty,
    prependBody: Seq[String] = Seq.empty
) extends NgPluginConfig {
  def json: JsValue = Json.obj(
    "append_head"  -> appendHead,
    "append_body"  -> appendBody,
    "prepend_head" -> prependHead,
    "prepend_body" -> prependBody
  )
}

class NgHtmlPatcher extends NgRequestTransformer {

  override def name: String                      = "Html Patcher"
  override def steps: Seq[NgStep]                = Seq(NgStep.TransformResponse)
  override def categories: Seq[NgPluginCategory] = Seq(NgPluginCategory.Transformations)
  override def visibility: NgPluginVisibility    = NgPluginVisibility.NgUserLand

  override def multiInstance: Boolean                      = true
  override def core: Boolean                               = true
  override def usesCallbacks: Boolean                      = false
  override def transformsRequest: Boolean                  = true
  override def transformsResponse: Boolean                 = true
  override def transformsError: Boolean                    = false
  override def isTransformRequestAsync: Boolean            = false
  override def isTransformResponseAsync: Boolean           = true
  override def description: Option[String]                 =
    "This plugin can inject elements in html pages (in the body or in the head) returned by the service".some
  override def defaultConfigObject: Option[NgPluginConfig] = NgHtmlPatcherConfig().some

  private def applyEl(str: String, ctx: NgTransformerResponseContext)(using env: Env): String = {
    GlobalExpressionLanguage(
      value = str,
      req = ctx.request.some,
      service = ctx.route.legacy.some,
      route = ctx.route.some,
      apiKey = ctx.apikey,
      user = ctx.user,
      context = ctx.attrs.get(otoroshi.plugins.Keys.ElCtxKey).getOrElse(Map.empty),
      attrs = ctx.attrs,
      env = env
    )
  }

  override def transformResponse(
      ctx: NgTransformerResponseContext
  )(using env: Env, ec: ExecutionContext, mat: Materializer): Future[Either[Result, NgPluginHttpResponse]] = {
    ctx.rawResponse.headers.get("Content-Type").orElse(ctx.rawResponse.headers.get("content-type")) match {
      case Some(ctype) if ctype.contains("text/html") =>
        val newHeaders    =
          ctx.otoroshiResponse.headers.-("Content-Length").-("content-length").+("Transfer-Encoding" -> "chunked")
        val isGzip        = ctx.otoroshiResponse.headers.getIgnoreCase("Content-Encoding").contains("gzip")
        val processedBody = if (isGzip) {
          ctx.otoroshiResponse.body.via(GzipFlow.gunzip())
        } else {
          ctx.otoroshiResponse.body
        }
        val newBodySource = Source.future(
          processedBody.runFold(ByteString.empty)(_ ++ _)
            .map { bodyRaw =>
              val body                = bodyRaw.utf8String
              val appendHead          = ctx.config
                .select("appendHead")
                .asOpt[Seq[String]]
                .orElse(ctx.config.select("append_head").asOpt[Seq[String]])
                .getOrElse(Seq.empty)
              val prependHead         = ctx.config
                .select("prependHead")
                .asOpt[Seq[String]]
                .orElse(ctx.config.select("prepend_head").asOpt[Seq[String]])
                .getOrElse(Seq.empty)
              val appendBody          = ctx.config
                .select("appendBody")
                .asOpt[Seq[String]]
                .orElse(ctx.config.select("append_body").asOpt[Seq[String]])
                .getOrElse(Seq.empty)
              val prependBody         = ctx.config
                .select("prependBody")
                .asOpt[Seq[String]]
                .orElse(ctx.config.select("prepend_body").asOpt[Seq[String]])
                .getOrElse(Seq.empty)
              val beforeHeadInjection = applyEl(prependHead.mkString(""), ctx)
              val afterHeadInjection  = applyEl(appendHead.mkString(""), ctx)
              val beforeBodyInjection = applyEl(prependBody.mkString(""), ctx)
              val afterBodyInjection  = applyEl(appendBody.mkString(""), ctx)
              val newBody             = body
                .replace("<head>", s"<head>$beforeHeadInjection")
                .replace("</head>", s"$afterHeadInjection</head>")
                .replace("<body>", s"<body>$beforeBodyInjection")
                .replace("</body>", s"$afterBodyInjection</body>")
              ByteString(newBody)
            }
        )
        ctx.otoroshiResponse
          .copy(headers = newHeaders, body = newBodySource.applyOnIf(isGzip)(_.via(GzipFlow.gzip())))
          .right
          .future
      case _                                          => ctx.otoroshiResponse.rightf
    }
  }
}
