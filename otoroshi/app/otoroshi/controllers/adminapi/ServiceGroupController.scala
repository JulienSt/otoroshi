package otoroshi.controllers.adminapi

import org.apache.pekko.stream.Materializer
import otoroshi.actions.ApiAction
import otoroshi.env.Env
import otoroshi.events._
import otoroshi.models.ServiceGroup
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
import otoroshi.utils.syntax.implicits._
import play.api.libs.json._
import play.api.mvc.{AbstractController, ControllerComponents, RequestHeader}

import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc
import play.api.mvc.AnyContent

class ServiceGroupController(val ApiAction: ApiAction, val cc: ControllerComponents)(implicit val env: Env)
    extends AbstractController(cc)
    with BulkControllerHelper[ServiceGroup, JsValue]
    with CrudControllerHelper[ServiceGroup, JsValue] {

  implicit val ec: ExecutionContext = env.otoroshiExecutionContext
  implicit val mat: Materializer    = env.otoroshiMaterializer

  override def singularName: String = "service-group"

  override def buildError(status: Int, message: String): ApiError[JsValue] =
    JsonApiError(status, play.api.libs.json.JsString(message))

  override def extractId(entity: ServiceGroup): String = entity.id

  override def readEntity(json: JsValue): Either[JsValue, ServiceGroup] =
    ServiceGroup._fmt.reads(json).asEither match {
      case Left(e)  => Left(JsError.toJson(e))
      case Right(r) => Right(r)
    }

  override def writeEntity(entity: ServiceGroup): JsValue = ServiceGroup._fmt.writes(entity)

  override def findByIdOps(id: String, req: RequestHeader)(implicit
      env: Env,
      ec: ExecutionContext
  ): Future[Either[ApiError[JsValue], OptionalEntityAndContext[ServiceGroup]]] = {
    env.datastores.serviceGroupDataStore.findById(id).map { opt =>
      Right(
        OptionalEntityAndContext(
          entity = opt,
          action = "ACCESS_SERVICES_GROUP",
          message = "User accessed a service group",
          metadata = Json.obj("serviceGroupId" -> id),
          alert = "ServiceGroupAccessed"
        )
      )
    }
  }

  override def findAllOps(
      req: RequestHeader
  )(implicit env: Env, ec: ExecutionContext): Future[Either[ApiError[JsValue], SeqEntityAndContext[ServiceGroup]]] = {
    env.datastores.serviceGroupDataStore.findAll().map { seq =>
      Right(
        SeqEntityAndContext(
          entity = seq,
          action = "ACCESS_ALL_SERVICES_GROUPS",
          message = "User accessed all services groups",
          metadata = Json.obj(),
          alert = "ServiceGroupsAccessed"
        )
      )
    }
  }

  override def createEntityOps(
      entity: ServiceGroup,
      req: RequestHeader
  )(implicit env: Env, ec: ExecutionContext): Future[Either[ApiError[JsValue], EntityAndContext[ServiceGroup]]] = {
    entity.save().map {
      case true  =>
        Right(
          EntityAndContext(
            entity = entity,
            action = "CREATE_SERVICE_GROUP",
            message = "User created a service group",
            metadata = entity.toJson.as[JsObject],
            alert = "ServiceGroupCreatedAlert"
          )
        )
      case false =>
        Left(
          JsonApiError(
            500,
            Json.obj("error" -> "Service group not stored ...")
          )
        )
    }
  }

  override def updateEntityOps(
      entity: ServiceGroup,
      req: RequestHeader
  )(implicit env: Env, ec: ExecutionContext): Future[Either[ApiError[JsValue], EntityAndContext[ServiceGroup]]] = {
    entity.save().map {
      case true  =>
        Right(
          EntityAndContext(
            entity = entity,
            action = "UPDATE_SERVICE_GROUP",
            message = "User updated a service group",
            metadata = entity.toJson.as[JsObject],
            alert = "ServiceGroupUpdatedAlert"
          )
        )
      case false =>
        Left(
          JsonApiError(
            500,
            Json.obj("error" -> "Service group not stored ...")
          )
        )
    }
  }

  override def deleteEntityOps(
      id: String,
      req: RequestHeader
  )(implicit env: Env, ec: ExecutionContext): Future[Either[ApiError[JsValue], NoEntityAndContext[ServiceGroup]]] = {
    env.datastores.serviceGroupDataStore.delete(id).map {
      case true  =>
        Right(
          NoEntityAndContext(
            action = "DELETE_SERVICE_GROUP",
            message = "User deleted a service group",
            metadata = Json.obj("serviceGroupId" -> id),
            alert = "ServiceGroupDeletedAlert"
          )
        )
      case false =>
        Left(
          JsonApiError(
            500,
            Json.obj("error" -> "Service group not deleted ...")
          )
        )
    }
  }

  def serviceGroupServices(serviceGroupId: String): mvc.Action[AnyContent] =
    ApiAction.async { ctx =>
      val paginationPage: Int     = ctx.request.queryString.get("page").flatMap(_.headOption).map(_.toInt).getOrElse(1)
      val paginationPageSize: Int =
        ctx.request.queryString.get("pageSize").flatMap(_.headOption).map(_.toInt).getOrElse(Int.MaxValue)
      val paginationPosition      = (paginationPage - 1) * paginationPageSize
      env.datastores.serviceGroupDataStore.findById(serviceGroupId).flatMap {
        case None                                   => NotFound(Json.obj("error" -> s"ServiceGroup with id: '$serviceGroupId' not found")).future
        case Some(group) if !ctx.canUserRead(group) => ctx.fforbidden
        case Some(group)                            =>
          Audit.send(
            AdminApiEvent(
              env.snowflakeGenerator.nextIdStr(),
              env.env,
              Some(ctx.apiKey),
              ctx.user,
              "ACCESS_SERVICES_FROM_SERVICES_GROUP",
              s"User accessed all services from a services group",
              ctx.from,
              ctx.ua,
              Json.obj("serviceGroupId" -> serviceGroupId)
            )
          )
          group.services
            .map(_.filter(ctx.canUserRead))
            .map(services => Ok(JsArray(services.drop(paginationPosition).take(paginationPageSize).map(_.toJson))))
      }
    }
}
