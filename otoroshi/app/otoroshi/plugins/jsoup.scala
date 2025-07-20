package otoroshi.plugins.jsoup

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import otoroshi.env.Env
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import otoroshi.next.plugins.api.{NgPluginCategory, NgPluginVisibility, NgStep}
import otoroshi.script.{HttpResponse, RequestTransformer, TransformerResponseBodyContext, TransformerResponseContext}
import play.api.mvc.Result
import otoroshi.utils.syntax.implicits._
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.{ExecutionContext, Future}

// MIGRATED
class HtmlPatcher extends RequestTransformer {

  override def name: String = "Html Patcher"

  override def defaultConfig: Option[JsObject] =
    Some(
      Json.obj(
        "HtmlPatcher" -> Json.obj(
          "appendHead" -> Json.arr(),
          "appendBody" -> Json.arr()
        )
      )
    )

  override def description: Option[String] =
    Some(
      s"""This plugin can inject elements in html pages (in the body or in the head) returned by the service
        |
        |This plugin can accept the following configuration
        |
        |```json
        |${Json.prettyPrint(defaultConfig.get)}
        |```
    """.stripMargin
    )

  override def visibility: NgPluginVisibility    = NgPluginVisibility.NgUserLand
  override def categories: Seq[NgPluginCategory] = Seq(NgPluginCategory.Transformations)
  override def steps: Seq[NgStep]                = Seq(NgStep.TransformResponse)

  private def parseElement(elementStr: String): Option[Element] = {
    if (elementStr.trim().isEmpty()) {
      None
    } else {
      val body = Jsoup.parseBodyFragment(elementStr).body()
      if (body.childrenSize() > 0) {
        body.children().first().some
      } else {
        body.some
      }
    }
  }

  override def transformResponseWithCtx(
      ctx: TransformerResponseContext
  )(implicit env: Env, ec: ExecutionContext, mat: Materializer): Future[Either[Result, HttpResponse]] = {
    val newHeaders =
      ctx.otoroshiResponse.headers.-("Content-Length").-("content-length").+("Transfer-Encoding" -> "chunked")
    ctx.otoroshiResponse.copy(headers = newHeaders).right.future
  }

  override def transformResponseBodyWithCtx(
      ctx: TransformerResponseBodyContext
  )(implicit env: Env, ec: ExecutionContext, mat: Materializer): Source[ByteString, _] = {
    ctx.rawResponse.headers.get("Content-Type").orElse(ctx.rawResponse.headers.get("content-type")) match {
      case Some(ctype) if ctype.contains("text/html") =>
          Source.future(
            ctx.body.runFold(ByteString.empty)(_ ++ _).map { bodyRaw =>
              val body       = bodyRaw.utf8String
              val doc        = Jsoup.parse(body)
              val config     = ctx.configFor("HtmlPatcher")
              val appendHead = config.select("appendHead").asOpt[Seq[String]].getOrElse(Seq.empty)
              val appendBody = config.select("appendBody").asOpt[Seq[String]].getOrElse(Seq.empty)
              parseElement(appendHead.mkString("\n")).map { elementHead =>
                doc.head().insertChildren(-1, elementHead)
              }
              parseElement(appendBody.mkString("\n")).map { elementBody =>
                doc.body().insertChildren(-1, elementBody)
              }
              ByteString(doc.toString)
            }
          )
      case _                                          => ctx.body
    }
  }
}
