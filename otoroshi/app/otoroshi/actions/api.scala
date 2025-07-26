package otoroshi.actions

import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import org.apache.pekko.http.scaladsl.util.FastFuture
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.common.base.Charsets
import next.models.Api
import otoroshi.env.Env
import otoroshi.gateway.Errors
import otoroshi.models.{ApiKey, BackOfficeUser, EntityLocationSupport, _}
import otoroshi.models.RightsChecker.{SuperAdminOnly, TenantAdminOnly}
import otoroshi.utils.syntax.implicits._
import play.api.Logger
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.mvc._
import otoroshi.security.{IdGenerator, OtoroshiClaim}
import otoroshi.utils.{JsonPathValidator, JsonValidator}
import otoroshi.utils.http.RequestImplicits._

import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object ApiActionContext {
  val forbidden: Result = Results.Forbidden(Json.obj("error" -> "You're not authorized here !"))
  val fforbidden        = forbidden.future
}

trait ApiActionContextCapable {

  def apiKey: ApiKey
  def request: RequestHeader

  lazy val forbidden  = ApiActionContext.forbidden
  lazy val fforbidden = ApiActionContext.fforbidden

  def user(using env: Env): Option[JsValue] =
    request.headers
      .get(env.Headers.OtoroshiAdminProfile)
      .flatMap(p => Try(Json.parse(new String(Base64.getDecoder.decode(p), StandardCharsets.UTF_8))).toOption)

  def from(using env: Env): String = request.theIpAddress

  def ua: String = request.theUserAgent

  lazy val currentTenant: TenantId = {
    TenantId(request.headers.get("Otoroshi-Tenant").getOrElse("default"))
  }

  private val bouRef = new AtomicReference[Either[String, Option[BackOfficeUser]]]()

  def userIsSuperAdmin(using env: Env): Boolean = {
    backOfficeUser match {
      case Left(_)           => false
      case Right(None)       =>
        val tenantAccess = apiKey.metadata
          .get("otoroshi-access-rights")
          .map(Json.parse)
          .flatMap(_.asOpt[JsArray])
          .map(UserRights.readFromArray)
        tenantAccess match {
          case None             => true
          case Some(userRights) => userRights.superAdmin
        }
      case Right(Some(user)) => user.rights.superAdmin
    }
  }

  def oneAuthorizedTenant(using env: Env): TenantId =
    backOfficeUser.toOption.flatten.map(_.rights.oneAuthorizedTenant).getOrElse(TenantId.default)

  def oneAuthorizedTeam(using env: Env): TeamId =
    backOfficeUser.toOption.flatten.map(_.rights.oneAuthorizedTeam).getOrElse(TeamId.default)

  def backOfficeUser(using env: Env): Either[String, Option[BackOfficeUser]] = {
    Option(bouRef.get()).getOrElse {
      (
        if (env.bypassUserRightsCheck) {
          Right(None)
        } else {
          request.headers.get("Otoroshi-BackOffice-User") match {
            case None          =>
              val tenantAccess = apiKey.metadata
                .get("otoroshi-access-rights")
                .map(Json.parse)
                .flatMap(_.asOpt[JsArray])
                .map(UserRights.readFromArray)
              tenantAccess match {
                case None             => Right(None)
                case Some(userRights) =>
                  val user = BackOfficeUser(
                    randomId = IdGenerator.token,
                    name = apiKey.clientName,
                    email = apiKey.clientId,
                    profile = Json.obj(),
                    authConfigId = "apikey",
                    simpleLogin = false,
                    tags = Seq.empty,
                    metadata = Map.empty,
                    rights = userRights,
                    location = EntityLocation(currentTenant, teams = Seq(TeamId.all)),
                    adminEntityValidators = Map.empty
                  )
                  Right(user.some)
                case _                => Left("You're not authorized here (invalid setup) ! ")
              }
            case Some(userJwt) =>
              Try(JWT.require(Algorithm.HMAC512(apiKey.clientSecret)).build().verify(userJwt)) match {
                case Failure(e)       =>
                  Left("You're not authorized here !")
                case Success(decoded) =>
                  Option(decoded.getClaim("user"))
                    .flatMap(c => Try(c.asString()).toOption)
                    .flatMap(u => Try(Json.parse(u)).toOption)
                    .flatMap(u => BackOfficeUser.fmt.reads(u).asOpt) match {
                    case None       => Left("You're not authorized here !")
                    case Some(user) => Right(user.some)
                  }
              }
          }
        }
      ).debug { either =>
        bouRef.set(either)
      }
    }
  }

  private def rootOrTenantAdmin(user: BackOfficeUser)(f: => Boolean)(using env: Env): Boolean = {
    if (env.bypassUserRightsCheck || SuperAdminOnly.canPerform(user, currentTenant)) { // || TenantAdminOnly.canPerform(user, currentTenant)) {
      true
    } else {
      f
    }
  }

  def canUserReadJson(item: JsValue)(using env: Env): Boolean = {
    canUserRead(FakeEntityLocationSupport(EntityLocation.readFromKey(item)))
  }

  def canUserRead[T <: EntityLocationSupport](item: T)(using env: Env): Boolean = {
    backOfficeUser match {
      case Left(_)           => false
      case Right(None)       => true
      case Right(Some(user)) =>
        rootOrTenantAdmin(user) {
          item match {
            case _: Tenant =>
              user.rights.canReadTenant(item.location.tenant)
            case _         =>
              (currentTenant.value == item.location.tenant.value || item.location.tenant == TenantId.all) && user.rights
                .canReadTenant(item.location.tenant) && user.rights.canReadTeams(currentTenant, item.location.teams)
          }
        }
    }
  }

  def canUserWriteJson(item: JsValue)(using env: Env): Boolean = {
    canUserWrite(FakeEntityLocationSupport(EntityLocation.readFromKey(item)))
  }

  def canUserWrite[T <: EntityLocationSupport](item: T)(using env: Env): Boolean = {
    backOfficeUser match {
      case Left(_)           => false
      case Right(None)       => true
      case Right(Some(user)) =>
        rootOrTenantAdmin(user) {
          item match {
            case _: Tenant =>
              (currentTenant.value == item.location.tenant.value || item.location.tenant == TenantId.all) && user.rights
                .canWriteTenant(item.location.tenant)
            case _         =>
              (currentTenant.value == item.location.tenant.value || item.location.tenant == TenantId.all) && user.rights
                .canWriteTenant(item.location.tenant) && user.rights.canWriteTeams(currentTenant, item.location.teams)
          }
        }
    }
  }

  def checkRights(rc: RightsChecker)(f: Future[Result])(using ec: ExecutionContext, env: Env): Future[Result] = {
    if (env.bypassUserRightsCheck) {
      f
    } else {
      backOfficeUser match {
        case Left(error)       => Results.Forbidden(Json.obj("error" -> error)).future
        case Right(None)       => f // standard api usage without limitations
        case Right(Some(user)) =>
          if (rc.canPerform(user, currentTenant)) {
            f
          } else {
            Results.Forbidden(Json.obj("error" -> "You're not authorized here !")).future
          }
      }
    }
  }

  private def findServiceById(
      serviceId: String
  )(using ec: ExecutionContext, env: Env): Future[Option[ServiceDescriptor]] = {
    def getRouteCompositions = {
      env.datastores.routeCompositionDataStore.findById(serviceId) flatMap {
        case Some(service) => service.toRoutes.head.legacy.some.vfuture
        case None          => getDraftRoutes
      }
    }

    def getDraftRoutes: Future[Option[ServiceDescriptor]] = {
      env.datastores.draftsDataStore.findById(serviceId) flatMap {
        case Some(service) =>
          val api = Api.format.reads(service.content).get
          if (api.routes.isEmpty) {
            None.vfuture
          } else {
            api.routeToNgRoute(api.routes.head).map(_.get.legacy.some)
          }
        case None          =>
          None.vfuture
      }
    }

    env.datastores.serviceDescriptorDataStore.findById(serviceId) flatMap {
      case Some(service) => service.some.vfuture
      case None          =>
        env.datastores.routeDataStore.findById(serviceId) flatMap {
          case Some(service) => service.legacy.some.vfuture
          case None          =>
            env.datastores.apiDataStore.findById(serviceId) flatMap {
              case Some(api) => api.legacy.some.vfuture
              case None      =>
                env.datastores.apiDataStore.findAll() flatMap { apis =>
                  apis.find(api => api.routes.exists(_.id == serviceId)) match {
                    case Some(api) =>
                      api.apiRouteToNgRoute(serviceId).flatMap {
                        case Some(route) => route.legacy.some.future
                        case _           => getRouteCompositions
                      }
                    case None      => getRouteCompositions
                  }
                }
            }
        }
    }
  }

  /// utils methods
  def canReadService(id: String)(f: => Future[Result])(using ec: ExecutionContext, env: Env): Future[Result] = {
    if (id == "global") {
      f
    } else {
      findServiceById(id).flatMap {
        case Some(service) if canUserRead(service) => f
        case _                                     => fforbidden
      }
    }
  }

  def canWriteService(id: String)(f: => Future[Result])(using ec: ExecutionContext, env: Env): Future[Result] = {
    findServiceById(id).flatMap {
      case Some(service) if canUserWrite(service) => f
      case _                                      => fforbidden
    }
  }

  def canReadApikey(id: String)(f: => Future[Result])(using ec: ExecutionContext, env: Env): Future[Result] = {
    env.datastores.apiKeyDataStore.findById(id).flatMap {
      case Some(service) if canUserRead(service) => f
      case _                                     => fforbidden
    }
  }

  def canWriteApikey(id: String)(f: => Future[Result])(using ec: ExecutionContext, env: Env): Future[Result] = {
    env.datastores.apiKeyDataStore.findById(id).flatMap {
      case Some(service) if canUserWrite(service) => f
      case _                                      => fforbidden
    }
  }

  def canReadGroup(id: String)(f: => Future[Result])(using ec: ExecutionContext, env: Env): Future[Result] = {
    env.datastores.serviceGroupDataStore.findById(id).flatMap {
      case Some(service) if canUserRead(service) => f
      case _                                     => fforbidden
    }
  }

  def canWriteGroup(id: String)(f: => Future[Result])(using ec: ExecutionContext, env: Env): Future[Result] = {
    env.datastores.serviceGroupDataStore.findById(id).flatMap {
      case Some(service) if canUserWrite(service) => f
      case _                                      => fforbidden
    }
  }

  def canWriteAuthModule(id: String)(f: => Future[Result])(using ec: ExecutionContext, env: Env): Future[Result] = {
    // env.datastores.authConfigsDataStore.findById(id).flatMap {
    env.proxyState.authModuleAsync(id).flatMap {
      case Some(mod) if canUserWrite(mod) => f
      case _                              => fforbidden
    }
  }

  def validateEntity(json: JsValue, singularName: String)(using env: Env): Either[JsValue, JsValue] = {
    backOfficeUser match {
      case Left(err)         => Left(err.json)
      case Right(None)       => Right(json)
      case Right(Some(user)) =>
        val envValidators: Seq[JsonValidator]  =
          env.adminEntityValidators.getOrElse("all", Seq.empty[JsonValidator]) ++ env.adminEntityValidators
            .getOrElse(singularName.toLowerCase, Seq.empty[JsonValidator])
        val userValidators: Seq[JsonValidator] =
          user.adminEntityValidators.getOrElse("all", Seq.empty[JsonValidator]) ++ user.adminEntityValidators
            .getOrElse(singularName.toLowerCase, Seq.empty[JsonValidator])
        val validators                         = envValidators ++ userValidators
        val failedValidators                   = validators.filterNot(_.validate(json))
        if (failedValidators.isEmpty) {
          Right(json)
        } else {
          val errors = failedValidators.flatMap(_.error).map(_.json)
          if (errors.isEmpty) {
            Left("entity validation failed".json)
          } else {
            Left(JsArray(errors))
          }
        }
    }
  }
}

case class ApiActionContext[A](apiKey: ApiKey, request: Request[A]) extends ApiActionContextCapable {}

class ApiAction(val parser: BodyParser[AnyContent])(using env: Env)
    extends ActionBuilder[ApiActionContext, AnyContent]
    with ActionFunction[Request, ApiActionContext] {

  implicit lazy val ec: ExecutionContext = env.otoroshiExecutionContext

  lazy val logger: Logger = Logger("otoroshi-api-action")

  def decodeBase64(encoded: String): String = new String(OtoroshiClaim.decoder.decode(encoded), StandardCharsets.UTF_8)

  def error(message: String, ex: Option[Throwable] = None)(using request: Request[?]): Future[Result] = {
    ex match {
      case Some(e) => logger.error(s"error message: $message", e)
      case None    => logger.error(s"error message: $message")
    }
    FastFuture.successful(
      Results
        .Unauthorized(Json.obj("error" -> message))
        .withHeaders(
          env.Headers.OtoroshiStateResp -> request.headers.get(env.Headers.OtoroshiState).getOrElse("--")
        )
    )
  }

  override def invokeBlock[A](request: Request[A], block: ApiActionContext[A] => Future[Result]): Future[Result] = {

    given req: Request[A] = request

    val host = request.theDomain // if (request.host.contains(":")) request.host.split(":")(0) else request.host
    def perform(): Future[Result] = {
      request.headers.get(env.Headers.OtoroshiClaim).get.split("\\.").toSeq match {
        case Seq(head, body, signature) =>
          val claim          = Json.parse(new String(OtoroshiClaim.decoder.decode(body), StandardCharsets.UTF_8))
          val lastestApikey  = (claim \ "access_type").asOpt[String].exists(v => v == "apikey" || v == "both")
          val latestClientId = (claim \ "apikey" \ "clientId").asOpt[String]
          (claim \ "sub").as[String].split(":").toSeq match {
            case Seq("apikey", clientId)                        =>
              env.datastores.globalConfigDataStore
                .singleton()
                .filter(c => request.method.toLowerCase() == "get" || !c.apiReadOnly)
                .flatMap { _ =>
                  env.datastores.apiKeyDataStore.findById(clientId).flatMap {
                    case Some(apikey)
                        if apikey.authorizedOnGroup(env.backOfficeGroup.id) || apikey
                          .authorizedOnService(env.backOfficeDescriptor.id) =>
                      block(ApiActionContext(apikey, request)).foldM {
                        case Success(res) =>
                          res
                            .withHeaders(
                              env.Headers.OtoroshiStateResp -> request.headers
                                .get(env.Headers.OtoroshiState)
                                .getOrElse("--")
                            )
                            .asFuture
                        case Failure(err) => error(s"Server error : $err", Some(err))
                      }
                    case _ => error(s"You're not authorized - ${request.method} ${request.uri}")
                  }
                } recoverWith { case e =>
                e.printStackTrace()
                error(s"You're not authorized - ${request.method} ${request.uri}")
              }
            case _ if lastestApikey && latestClientId.isDefined =>
              env.datastores.globalConfigDataStore
                .singleton()
                .filter(c => request.method.toLowerCase() == "get" || !c.apiReadOnly)
                .flatMap { _ =>
                  env.datastores.apiKeyDataStore.findById(latestClientId.get).flatMap {
                    case Some(apikey)
                        if apikey.authorizedOnGroup(env.backOfficeGroup.id) || apikey
                          .authorizedOnService(env.backOfficeDescriptor.id) =>
                      block(ApiActionContext(apikey, request)).foldM {
                        case Success(res) =>
                          res
                            .withHeaders(
                              env.Headers.OtoroshiStateResp -> request.headers
                                .get(env.Headers.OtoroshiState)
                                .getOrElse("--")
                            )
                            .asFuture
                        case Failure(err) => error(s"Server error : $err", Some(err))
                      }
                    case _ => error(s"You're not authorized - ${request.method} ${request.uri}")
                  }
                } recoverWith { case e =>
                e.printStackTrace()
                error(s"You're not authorized - ${request.method} ${request.uri}")
              }
            case _                                              => error(s"You're not authorized - ${request.method} ${request.uri}")
          }
        case _                          => error(s"You're not authorized - ${request.method} ${request.uri}")
      }
    }
    host match {
      case env.adminApiHost                     => perform()
      case h if env.adminApiDomains.contains(h) => perform()
      case _                                    => error(s"Not found")
    }
  }

  override protected def executionContext: ExecutionContext = ec
}

case class UnAuthApiActionContent[A](req: Request[A])

class UnAuthApiAction(val parser: BodyParser[AnyContent])(using env: Env)
    extends ActionBuilder[UnAuthApiActionContent, AnyContent]
    with ActionFunction[Request, UnAuthApiActionContent] {

  implicit lazy val ec: ExecutionContext = env.otoroshiExecutionContext

  lazy val logger: Logger = Logger("otoroshi-api-action")

  def error(message: String, ex: Option[Throwable] = None)(using request: Request[?]): Future[Result] = {
    ex match {
      case Some(e) => logger.error(s"error message: $message", e)
      case None    => logger.error(s"error message: $message")
    }
    FastFuture.successful(
      Results
        .Unauthorized(Json.obj("error" -> message))
        .withHeaders(
          env.Headers.OtoroshiStateResp -> request.headers.get(env.Headers.OtoroshiState).getOrElse("--")
        )
    )
  }

  override def invokeBlock[A](
      request: Request[A],
      block: UnAuthApiActionContent[A] => Future[Result]
  ): Future[Result] = {

    given req: Request[A] = request

    val host = request.theDomain // if (request.host.contains(":")) request.host.split(":")(0) else request.host
    host match {
      case env.adminApiHost                     => block(UnAuthApiActionContent(request))
      case h if env.adminApiDomains.contains(h) => block(UnAuthApiActionContent(request))
      case _                                    => error(s"Not found")
    }
  }

  override protected def executionContext: ExecutionContext = ec
}
