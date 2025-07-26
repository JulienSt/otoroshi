package otoroshi.controllers.adminapi

import otoroshi.actions.ApiAction
import org.apache.pekko.http.scaladsl.util.FastFuture
import org.apache.pekko.stream.Materializer
import otoroshi.env.Env
import otoroshi.utils.controllers.{
  ApiError,
  BulkControllerHelper,
  CrudControllerHelper,
  EntityAndContext,
  JsonApiError,
  NoEntityAndContext,
  OptionalEntityAndContext,
  SeqEntityAndContext
}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{AbstractController, ControllerComponents, RequestHeader}
import otoroshi.ssl.Cert

import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc
import play.api.mvc.AnyContent

class CertificatesController(val ApiAction: ApiAction, val cc: ControllerComponents)(using val env: Env)
    extends AbstractController(cc)
    with BulkControllerHelper[Cert, JsValue]
    with CrudControllerHelper[Cert, JsValue] {

  implicit lazy val ec: ExecutionContext = env.otoroshiExecutionContext
  implicit lazy val mat: Materializer    = env.otoroshiMaterializer

  lazy val logger: Logger = Logger("otoroshi-certificates-api")

  override def singularName: String = "certificate"

  override def buildError(status: Int, message: String): ApiError[JsValue] =
    JsonApiError(status, play.api.libs.json.JsString(message))

  def renewCert(id: String): mvc.Action[AnyContent] =
    ApiAction.async { ctx =>
      env.datastores.certificatesDataStore.findById(id).map(_.map(_.enrich())).flatMap {
        case None       => FastFuture.successful(NotFound(Json.obj("error" -> s"No Certificate found")))
        case Some(cert) => cert.renew().map(c => Ok(c.toJson))
      }
    }

  override def extractId(entity: Cert): String = entity.id

  override def readEntity(json: JsValue): Either[JsValue, Cert] =
    Cert._fmt.reads(json).asEither match {
      case Left(e)  => Left(JsError.toJson(e))
      case Right(r) => Right(r)
    }

  override def writeEntity(entity: Cert): JsValue = Cert._fmt.writes(entity)

  override def findByIdOps(
      id: String,
      req: RequestHeader
  )(using env: Env, ec: ExecutionContext): Future[Either[ApiError[JsValue], OptionalEntityAndContext[Cert]]] = {
    env.datastores.certificatesDataStore.findById(id).map { opt =>
      Right(
        OptionalEntityAndContext(
          entity = opt,
          action = "ACCESS_CERTIFICATE",
          message = "User accessed a certificate",
          metadata = Json.obj("CertId" -> id),
          alert = "CertAccessed"
        )
      )
    }
  }

  override def findAllOps(
      req: RequestHeader
  )(using env: Env, ec: ExecutionContext): Future[Either[ApiError[JsValue], SeqEntityAndContext[Cert]]] = {
    val keypair = req.queryString.get("keypair").map(_.last).getOrElse("false").toBoolean
    env.datastores.certificatesDataStore.findAll().map { seq =>
      Right(
        SeqEntityAndContext(
          entity = if (keypair) seq.filter(_.keypair) else seq,
          action = "ACCESS_ALL_CERTIFICATES",
          message = "User accessed all certificates",
          metadata = Json.obj(),
          alert = "CertsAccessed"
        )
      )
    }
  }

  override def createEntityOps(
      entity: Cert,
      req: RequestHeader
  )(using env: Env, ec: ExecutionContext): Future[Either[ApiError[JsValue], EntityAndContext[Cert]]] = {
    val noEnrich = req.getQueryString("enrich").contains("false")
    val enriched = if (noEnrich) entity else entity.enrich()
    env.datastores.certificatesDataStore.set(enriched).map {
      case true  =>
        Right(
          EntityAndContext(
            entity = entity,
            action = "CREATE_CERTIFICATE",
            message = "User created a certificate",
            metadata = entity.toJson.as[JsObject],
            alert = "CertCreatedAlert"
          )
        )
      case false =>
        Left(
          JsonApiError(
            500,
            Json.obj("error" -> "certificate not stored ...")
          )
        )
    }
  }

  override def updateEntityOps(
      entity: Cert,
      req: RequestHeader
  )(using env: Env, ec: ExecutionContext): Future[Either[ApiError[JsValue], EntityAndContext[Cert]]] = {
    val noEnrich = req.getQueryString("enrich").contains("false")
    val enriched = if (noEnrich) entity else entity.enrich()
    env.datastores.certificatesDataStore.set(enriched).map {
      case true  =>
        Right(
          EntityAndContext(
            entity = entity,
            action = "UPDATE_CERTIFICATE",
            message = "User updated a certificate",
            metadata = entity.toJson.as[JsObject],
            alert = "CertUpdatedAlert"
          )
        )
      case false =>
        Left(
          JsonApiError(
            500,
            Json.obj("error" -> "certificate not stored ...")
          )
        )
    }
  }

  override def deleteEntityOps(
      id: String,
      req: RequestHeader
  )(using env: Env, ec: ExecutionContext): Future[Either[ApiError[JsValue], NoEntityAndContext[Cert]]] = {
    env.datastores.certificatesDataStore.delete(id).map {
      case true  =>
        Right(
          NoEntityAndContext(
            action = "DELETE_CERTIFICATE",
            message = "User deleted a certificate",
            metadata = Json.obj("CertId" -> id),
            alert = "CertDeletedAlert"
          )
        )
      case false =>
        Left(
          JsonApiError(
            500,
            Json.obj("error" -> "certificate not deleted ...")
          )
        )
    }
  }
}
