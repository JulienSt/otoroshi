package otoroshi.next.extensions

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import otoroshi.actions.{ApiAction, BackOfficeAction, PrivateAppsAction}
import otoroshi.api.Resource
import otoroshi.env.Env
import otoroshi.models.{ApiKey, BackOfficeUser, EntityLocationSupport, PrivateAppsUser}
import otoroshi.next.utils.Vault
import otoroshi.storage.DataStoresBuilder
import otoroshi.utils.cache.types.UnboundedTrieMap
import otoroshi.utils.syntax.implicits._
import play.api.Configuration
import play.api.libs.json.{JsObject, JsResult, JsValue, Reads}
import play.api.mvc._
import play.twirl.api.Html

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class KubernetesResourceReader(f: JsValue => JsResult[JsValue]) extends Reads[JsValue] {
  override def reads(json: JsValue): JsResult[JsValue] = f(json)
}
object KubernetesHelper {
  def reader(f: JsValue => JsResult[JsValue]): Reads[JsValue] = new KubernetesResourceReader(f)
}

case class AdminExtensionId(value: String) {
  lazy val cleanup: String = value.replace(".", "_").toLowerCase()
}
case class AdminExtensionEntity[A <: EntityLocationSupport](resource: otoroshi.api.Resource)

case class AdminExtensionFrontendExtension(path: String)
case class AdminExtensionGlobalConfigExtension()
trait AdminExtensionRoute {
  def method: String
  def path: String
  def wantsBody: Boolean
  def routeId: String = s"$method$path"
}

class AdminExtensionRouterContext[A <: AdminExtensionRoute](
    underlying: org.bigtesting.routd.Route,
    val adminRoute: A,
    method: String,
    path: String,
    matchPath: String
) {
  def named(name: String): Option[String] = Option(underlying.getNamedParameter(name, matchPath))
  def splat(index: Int): Option[String]   = Option(underlying.getSplatParameter(index, matchPath))
}

class AdminExtensionRouter[A <: AdminExtensionRoute](routes: Seq[A]) {
  private val (router, config) = {
    var m = Map.empty[String, A]
    val r = new org.bigtesting.routd.TreeRouter()
    routes.foreach { route =>
      r.add(new org.bigtesting.routd.Route(route.routeId))
      m = m.+((route.routeId, route))
    }
    (r, m)
  }
  def find(request: RequestHeader): Option[AdminExtensionRouterContext[A]] = {
    val matchedPath = s"${request.method}${request.path}"
    Option(router.route(matchedPath)).flatMap { r =>
      config
        .get(r.getResourcePath)
        .map(rr => new AdminExtensionRouterContext[A](r, rr, request.method, request.path, matchedPath))
    }
  }
}

case class AdminExtensionAssetRoute(
    path: String,
    handle: (AdminExtensionRouterContext[AdminExtensionAssetRoute], RequestHeader) => Future[Result]
) extends AdminExtensionRoute {
  override def method: String     = "GET"
  override def wantsBody: Boolean = false
}
case class AdminExtensionBackofficeAuthRoute(
    method: String,
    path: String,
    wantsBody: Boolean,
    handle: (
        AdminExtensionRouterContext[AdminExtensionBackofficeAuthRoute],
        RequestHeader,
        Option[BackOfficeUser],
        Option[Source[ByteString, _]]
    ) => Future[Result]
) extends AdminExtensionRoute
case class AdminExtensionBackofficePublicRoute(
    method: String,
    path: String,
    wantsBody: Boolean,
    handle: (
        AdminExtensionRouterContext[AdminExtensionBackofficePublicRoute],
        RequestHeader,
        Option[Source[ByteString, _]]
    ) => Future[Result]
) extends AdminExtensionRoute
case class AdminExtensionAdminApiRoute(
    method: String,
    path: String,
    wantsBody: Boolean,
    handle: (
        AdminExtensionRouterContext[AdminExtensionAdminApiRoute],
        RequestHeader,
        ApiKey,
        Option[Source[ByteString, _]]
    ) => Future[Result]
) extends AdminExtensionRoute
case class AdminExtensionPrivateAppAuthRoute(
    method: String,
    path: String,
    wantsBody: Boolean,
    handle: (
        AdminExtensionRouterContext[AdminExtensionPrivateAppAuthRoute],
        RequestHeader,
        Seq[PrivateAppsUser],
        Option[Source[ByteString, _]]
    ) => Future[Result]
) extends AdminExtensionRoute
case class AdminExtensionPrivateAppPublicRoute(
    method: String,
    path: String,
    wantsBody: Boolean,
    handle: (
        AdminExtensionRouterContext[AdminExtensionPrivateAppPublicRoute],
        RequestHeader,
        Option[Source[ByteString, _]]
    ) => Future[Result]
) extends AdminExtensionRoute
case class AdminExtensionWellKnownRoute(
    method: String,
    path: String,
    wantsBody: Boolean,
    handle: (
        AdminExtensionRouterContext[AdminExtensionWellKnownRoute],
        RequestHeader,
        Option[Source[ByteString, _]]
    ) => Future[Result]
) extends AdminExtensionRoute

case class AdminExtensionConfig(enabled: Boolean)

case class AdminExtensionVault(name: String, build: (String, Configuration, Env) => Vault)

case class PublicKeyJwk(raw: JsValue)

trait AdminExtension {

  def env: Env

  def id: AdminExtensionId
  def enabled: Boolean
  def name: String
  def description: Option[String]

  def start(): Unit              = ()
  def stop(): Unit               = ()
  def syncStates(): Future[Unit] = ().vfuture

  // TODO: add util function to access and update global_config extensions with id cleanup as key

  def datastoreBuilders(): Map[String, DataStoresBuilder]                         = Map.empty
  def entities(): Seq[AdminExtensionEntity[EntityLocationSupport]]                = Seq.empty
  def frontendExtensions(): Seq[AdminExtensionFrontendExtension]                  = Seq.empty
  def globalConfigExtensions(): Seq[AdminExtensionGlobalConfigExtension]          = Seq.empty
  def assets(): Seq[AdminExtensionAssetRoute]                                     = Seq.empty
  def assetsOverrides(): Seq[AdminExtensionAssetRoute]                            = Seq.empty
  def backofficeAuthRoutes(): Seq[AdminExtensionBackofficeAuthRoute]              = Seq.empty
  def backofficeAuthOverridesRoutes(): Seq[AdminExtensionBackofficeAuthRoute]     = Seq.empty
  def backofficePublicRoutes(): Seq[AdminExtensionBackofficePublicRoute]          = Seq.empty
  def backofficePublicOverridesRoutes(): Seq[AdminExtensionBackofficePublicRoute] = Seq.empty
  def adminApiRoutes(): Seq[AdminExtensionAdminApiRoute]                          = Seq.empty
  def adminApiOverridesRoutes(): Seq[AdminExtensionAdminApiRoute]                 = Seq.empty
  def privateAppAuthRoutes(): Seq[AdminExtensionPrivateAppAuthRoute]              = Seq.empty
  def privateAppAuthOverridesRoutes(): Seq[AdminExtensionPrivateAppAuthRoute]     = Seq.empty
  def privateAppPublicRoutes(): Seq[AdminExtensionPrivateAppPublicRoute]          = Seq.empty
  def privateAppPublicOverridesRoutes(): Seq[AdminExtensionPrivateAppPublicRoute] = Seq.empty
  def wellKnownRoutes(): Seq[AdminExtensionWellKnownRoute]                        = Seq.empty
  def wellKnownOverridesRoutes(): Seq[AdminExtensionWellKnownRoute]               = Seq.empty
  def vaults(): Seq[AdminExtensionVault]                                          = Seq.empty
  def publicKeys(): Future[Seq[PublicKeyJwk]]                                     = Seq.empty.vfuture
  def configuration: Configuration                                                = env.configuration
    .getOptional[Configuration](s"otoroshi.admin-extensions.configurations.${id.cleanup}")
    .getOrElse(Configuration.empty)
}

object AdminExtensions {
  def current(env: Env, config: AdminExtensionConfig): AdminExtensions = {
    if (config.enabled) {
      val extensions = env.scriptManager.adminExtensionNames
        .map { name =>
          try {
            val clazz       = this.getClass.getClassLoader.loadClass(name)
            val constructor = (Option(clazz.getDeclaredConstructor(classOf[Env])).toSeq ++ Option(
              clazz.getConstructor(classOf[Env])
            ).toSeq).head
            val inst        = constructor.newInstance(env)
            Right(inst.asInstanceOf[AdminExtension])
          } catch {
            case e: Throwable =>
              e.printStackTrace()
              Left(e)
          }
        }
        .collect { case Right(ext) =>
          ext
        }
      new AdminExtensions(env, extensions)
    } else {
      new AdminExtensions(env, Seq.empty)
    }
  }
}

class AdminExtensions(env: Env, _extensions: Seq[AdminExtension]) {

  private implicit val ec: ExecutionContext = env.otoroshiExecutionContext
  private implicit val mat: Materializer = env.otoroshiMaterializer
  private implicit val ev: Env = env

  private val hasExtensions = _extensions.nonEmpty

  private val extensions: Seq[AdminExtension] = _extensions.filter(_.enabled)

  private val entitiesMap: Map[String, Seq[AdminExtensionEntity[EntityLocationSupport]]] =
    extensions.map(v => (v.id.cleanup, v.entities())).toMap
  private val entities: Seq[AdminExtensionEntity[EntityLocationSupport]]                 = entitiesMap.values.flatten.toSeq

  private val frontendExtensions: Seq[AdminExtensionFrontendExtension]         = extensions.flatMap(_.frontendExtensions())
  private val globalConfigExtensions: Seq[AdminExtensionGlobalConfigExtension] =
    extensions.flatMap(_.globalConfigExtensions())

  // ----------------------------------------------------------------------------------------------------------------
  private val assets: Seq[AdminExtensionAssetRoute]                                     = extensions.flatMap(_.assets())
  private val assetsRouter                                                              = new AdminExtensionRouter[AdminExtensionAssetRoute](assets)
  private val assetsOverrides: Seq[AdminExtensionAssetRoute]                            = extensions.flatMap(_.assetsOverrides())
  private val assetsOverridesRouter                                                     = new AdminExtensionRouter[AdminExtensionAssetRoute](assetsOverrides)
  // ----------------------------------------------------------------------------------------------------------------
  private val backofficeAuthRoutes: Seq[AdminExtensionBackofficeAuthRoute]              =
    extensions.flatMap(_.backofficeAuthRoutes())
  private val backofficeAuthRouter                                                      = new AdminExtensionRouter[AdminExtensionBackofficeAuthRoute](backofficeAuthRoutes)
  private val backofficeAuthOverridesRoutes: Seq[AdminExtensionBackofficeAuthRoute]     =
    extensions.flatMap(_.backofficeAuthOverridesRoutes())
  private val backofficeAuthOverridesRouter                                             =
    new AdminExtensionRouter[AdminExtensionBackofficeAuthRoute](backofficeAuthOverridesRoutes)
  // ----------------------------------------------------------------------------------------------------------------
  private val backofficePublicRoutes: Seq[AdminExtensionBackofficePublicRoute]          =
    extensions.flatMap(_.backofficePublicRoutes())
  private val backofficePublicRouter                                                    =
    new AdminExtensionRouter[AdminExtensionBackofficePublicRoute](backofficePublicRoutes)
  private val backofficePublicOverridesRoutes: Seq[AdminExtensionBackofficePublicRoute] =
    extensions.flatMap(_.backofficePublicOverridesRoutes())
  private val backofficePublicOverridesRouter                                           =
    new AdminExtensionRouter[AdminExtensionBackofficePublicRoute](backofficePublicOverridesRoutes)
  // ----------------------------------------------------------------------------------------------------------------
  private val adminApiOverridesRoutes: Seq[AdminExtensionAdminApiRoute]                 =
    extensions.flatMap(_.adminApiOverridesRoutes())
  private val adminApiOverridesRouter                                                   = new AdminExtensionRouter[AdminExtensionAdminApiRoute](adminApiOverridesRoutes)
  private val adminApiRoutes: Seq[AdminExtensionAdminApiRoute]                          = extensions.flatMap(_.adminApiRoutes())
  private val adminApiRouter                                                            = new AdminExtensionRouter[AdminExtensionAdminApiRoute](adminApiRoutes)
  // ----------------------------------------------------------------------------------------------------------------
  private val privateAppAuthRoutes: Seq[AdminExtensionPrivateAppAuthRoute]              =
    extensions.flatMap(_.privateAppAuthRoutes())
  private val privateAppAuthRouter                                                      = new AdminExtensionRouter[AdminExtensionPrivateAppAuthRoute](privateAppAuthRoutes)
  private val privateAppAuthOverridesRoutes: Seq[AdminExtensionPrivateAppAuthRoute]     =
    extensions.flatMap(_.privateAppAuthOverridesRoutes())
  private val privateAppAuthOverridesRouter                                             =
    new AdminExtensionRouter[AdminExtensionPrivateAppAuthRoute](privateAppAuthOverridesRoutes)
  // ----------------------------------------------------------------------------------------------------------------
  private val privateAppPublicRoutes: Seq[AdminExtensionPrivateAppPublicRoute]          =
    extensions.flatMap(_.privateAppPublicRoutes())
  private val privateAppPublicRouter                                                    =
    new AdminExtensionRouter[AdminExtensionPrivateAppPublicRoute](privateAppPublicRoutes)
  private val privateAppPublicOverridesRoutes: Seq[AdminExtensionPrivateAppPublicRoute] =
    extensions.flatMap(_.privateAppPublicOverridesRoutes())
  private val privateAppPublicOverridesRouter                                           =
    new AdminExtensionRouter[AdminExtensionPrivateAppPublicRoute](privateAppPublicOverridesRoutes)
  // ----------------------------------------------------------------------------------------------------------------
  private val wellKnownRoutes: Seq[AdminExtensionWellKnownRoute]                        = extensions.flatMap(_.wellKnownRoutes())
  private val wellKnownRouter                                                           = new AdminExtensionRouter[AdminExtensionWellKnownRoute](wellKnownRoutes)
  private val wellKnownOverridesRoutes: Seq[AdminExtensionWellKnownRoute]               =
    extensions.flatMap(_.wellKnownOverridesRoutes())
  private val wellKnownOverridesRouter                                                  =
    new AdminExtensionRouter[AdminExtensionWellKnownRoute](wellKnownOverridesRoutes)
  // ----------------------------------------------------------------------------------------------------------------
  private val vaults: Seq[AdminExtensionVault]                                          = extensions.flatMap(_.vaults())
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  private val extCache = new UnboundedTrieMap[Class[_], Any]

  def enabledExtensions(): JsValue = {
    JsObject(extensions.map(e => (e.id.value, e.enabled.json)))
  }

  def enabledExtensionsHtml(): Html = {
    Html(
      enabledExtensions().stringify
    )
  }

  def vault(name: String): Option[AdminExtensionVault] = vaults.find(_.name == name)

  def extension[A](implicit ct: ClassTag[A]): Option[A] = {
    if (hasExtensions) {
      extCache.get(ct.runtimeClass) match {
        case Some(any) => any.asInstanceOf[A].some
        case None      =>
          val opt = extensions
            .find { e =>
              e.getClass == ct.runtimeClass
            }
            .map(_.asInstanceOf[A])
          opt.foreach(ex => extCache.put(ct.runtimeClass, ex))
          opt
      }
    } else {
      None
    }
  }

  def datastoreFrom(extId: AdminExtensionId, name: String): Option[DataStoresBuilder] = {
    extensions.find(_.id == extId).flatMap(_.datastoreBuilders().get(name))
  }

  def datastore(name: String): Option[DataStoresBuilder] = {
    extensions
      .collect {
        case e if e.datastoreBuilders().nonEmpty => e.datastoreBuilders()
      }
      .foldLeft(Map.empty[String, DataStoresBuilder])((a, b) => a ++ b)
      .get(name)
  }

  def getAssetsCallHandler(
      request: RequestHeader,
      actionBuilder: ActionBuilder[Request, AnyContent]
  ): Option[AdminExtensionRouterContext[AdminExtensionAssetRoute]] = {
    if (hasExtensions && assetsOverrides.nonEmpty) {
      assetsOverridesRouter.find(request)
    } else if (hasExtensions && request.path.startsWith("/extensions/assets/") && assets.nonEmpty) {
      assetsRouter.find(request)
    } else None
  }

  def handleAssetsCall(
      request: RequestHeader,
      actionBuilder: ActionBuilder[Request, AnyContent]
  )(f: => Option[Handler]): Option[Handler] = {
    if (hasExtensions && assetsOverrides.nonEmpty) {
      assetsOverridesRouter.find(request) match {
        case Some(route) => Some(actionBuilder.async { ctx => route.adminRoute.handle(route, ctx) })
        case None        => f
      }
    } else if (hasExtensions && request.path.startsWith("/extensions/assets/") && assets.nonEmpty) {
      assetsRouter.find(request) match {
        case Some(route) => Some(actionBuilder.async { ctx => route.adminRoute.handle(route, ctx) })
        case None        => f
      }
    } else f
  }

  def handleWellKnownCall(
      request: RequestHeader,
      actionBuilder: ActionBuilder[Request, AnyContent],
      sourceBodyParser: BodyParser[Source[ByteString, _]]
  )(f: => Option[Handler]): Option[Handler] = {
    if (hasExtensions && wellKnownOverridesRoutes.nonEmpty) {
      wellKnownOverridesRouter.find(request) match {
        case None                                       => f
        case Some(route) if route.adminRoute.wantsBody  =>
          Some(actionBuilder.async(sourceBodyParser) { req => route.adminRoute.handle(route, req, req.body.some) })
        case Some(route) =>
          Some(actionBuilder.async { req => route.adminRoute.handle(route, req, None) })
      }
    } else if (
      hasExtensions && request.path.startsWith("/.well-known/otoroshi/extensions/") && wellKnownRoutes.nonEmpty
    ) {
      wellKnownRouter.find(request) match {
        case None                                       => f
        case Some(route) if route.adminRoute.wantsBody  =>
          Some(actionBuilder.async(sourceBodyParser) { req => route.adminRoute.handle(route, req, req.body.some) })
        case Some(route) =>
          Some(actionBuilder.async { req => route.adminRoute.handle(route, req, None) })
      }
    } else f
  }

  def handleAdminApiCall(
      request: RequestHeader,
      actionBuilder: ActionBuilder[Request, AnyContent],
      ApiAction: ApiAction,
      sourceBodyParser: BodyParser[Source[ByteString, _]]
  )(f: => Option[Handler]): Option[Handler] = {
    if (hasExtensions && adminApiOverridesRoutes.nonEmpty) {
      adminApiOverridesRouter.find(request) match {
        case Some(route) if route.adminRoute.wantsBody  =>
          Some(ApiAction.async(sourceBodyParser) { ctx =>
            route.adminRoute.handle(route, ctx.request, ctx.apiKey, ctx.request.body.some)
          })
        case Some(route) =>
          Some(ApiAction.async { ctx => route.adminRoute.handle(route, ctx.request, ctx.apiKey, None) })
        case None                                       => f
      }
    } else if (
      hasExtensions && (request.path
        .startsWith("/api/extensions/") || request.path.startsWith("/apis/extensions/")) && adminApiRoutes.nonEmpty
    ) {
      adminApiRouter.find(request) match {
        case Some(route) if route.adminRoute.wantsBody  =>
          Some(ApiAction.async(sourceBodyParser) { ctx =>
            route.adminRoute.handle(route, ctx.request, ctx.apiKey, ctx.request.body.some)
          })
        case Some(route) =>
          Some(ApiAction.async { ctx => route.adminRoute.handle(route, ctx.request, ctx.apiKey, None) })
        case None                                       => f
      }
    } else f
  }

  def handleBackofficeCall(
      request: RequestHeader,
      actionBuilder: ActionBuilder[Request, AnyContent],
      BackOfficeAction: BackOfficeAction,
      sourceBodyParser: BodyParser[Source[ByteString, _]]
  )(f: => Option[Handler]): Option[Handler] = {
    if (hasExtensions && assetsOverrides.nonEmpty) {
      assetsOverridesRouter.find(request) match {
        case Some(route) => Some(actionBuilder.async { ctx => route.adminRoute.handle(route, ctx) })
        case None        => f
      }
    } else if (hasExtensions && backofficePublicOverridesRoutes.nonEmpty) {
      backofficePublicOverridesRouter.find(request) match {
        case Some(route) if route.adminRoute.wantsBody  =>
          Some(actionBuilder.async(sourceBodyParser) { req => route.adminRoute.handle(route, req, req.body.some) })
        case Some(route) =>
          Some(actionBuilder.async { req => route.adminRoute.handle(route, req, None) })
        case None                                       => f
      }
    } else if (hasExtensions && backofficeAuthOverridesRoutes.nonEmpty) {
      backofficeAuthOverridesRouter.find(request) match {
        case Some(route) if route.adminRoute.wantsBody  =>
          Some(BackOfficeAction.async(sourceBodyParser) { ctx =>
            route.adminRoute.handle(route, ctx.request, ctx.user, ctx.request.body.some)
          })
        case Some(route) =>
          Some(BackOfficeAction.async { ctx => route.adminRoute.handle(route, ctx.request, ctx.user, None) })
        case None                                       => f
      }
    } else if (hasExtensions && request.path.startsWith("/extensions/assets/") && assets.nonEmpty) {
      assetsRouter.find(request) match {
        case Some(route) => Some(actionBuilder.async { ctx => route.adminRoute.handle(route, ctx) })
        case None        => f
      }
    } else if (hasExtensions && request.path.startsWith("/extensions/pub/") && backofficePublicRoutes.nonEmpty) {
      backofficePublicRouter.find(request) match {
        case Some(route) if route.adminRoute.wantsBody  =>
          Some(actionBuilder.async(sourceBodyParser) { req => route.adminRoute.handle(route, req, req.body.some) })
        case Some(route) =>
          Some(actionBuilder.async { req => route.adminRoute.handle(route, req, None) })
        case None                                       => f
      }
    } else if (hasExtensions && request.path.startsWith("/extensions/") && backofficeAuthRoutes.nonEmpty) {
      backofficeAuthRouter.find(request) match {
        case Some(route) if route.adminRoute.wantsBody  =>
          Some(BackOfficeAction.async(sourceBodyParser) { ctx =>
            route.adminRoute.handle(route, ctx.request, ctx.user, ctx.request.body.some)
          })
        case Some(route) =>
          Some(BackOfficeAction.async { ctx => route.adminRoute.handle(route, ctx.request, ctx.user, None) })
        case None                                       => f
      }
    } else f
  }

  def handlePrivateAppsCall(
      request: RequestHeader,
      actionBuilder: ActionBuilder[Request, AnyContent],
      PrivateAppsAction: PrivateAppsAction,
      sourceBodyParser: BodyParser[Source[ByteString, _]]
  )(f: => Option[Handler]): Option[Handler] = {
    if (hasExtensions && assetsOverrides.nonEmpty) {
      assetsOverridesRouter.find(request) match {
        case Some(route) => Some(actionBuilder.async { ctx => route.adminRoute.handle(route, ctx) })
        case None        => f
      }
    } else if (hasExtensions && privateAppPublicOverridesRoutes.nonEmpty) {
      privateAppPublicOverridesRouter.find(request) match {
        case Some(route) if route.adminRoute.wantsBody  =>
          Some(actionBuilder.async(sourceBodyParser) { req => route.adminRoute.handle(route, req, req.body.some) })
        case Some(route) =>
          Some(actionBuilder.async { req => route.adminRoute.handle(route, req, None) })
        case None                                       => f
      }
    } else if (hasExtensions && privateAppAuthOverridesRoutes.nonEmpty) {
      privateAppAuthOverridesRouter.find(request) match {
        case Some(route) if route.adminRoute.wantsBody  =>
          Some(PrivateAppsAction.async(sourceBodyParser) { ctx =>
            route.adminRoute.handle(route, ctx.request, ctx.users, ctx.request.body.some)
          })
        case Some(route) =>
          Some(PrivateAppsAction.async { ctx => route.adminRoute.handle(route, ctx.request, ctx.users, None) })
        case None                                       => f
      }
    } else if (hasExtensions && request.path.startsWith("/extensions/assets/") && assets.nonEmpty) {
      assetsRouter.find(request) match {
        case Some(route) => Some(actionBuilder.async { ctx => route.adminRoute.handle(route, ctx) })
        case None        => f
      }
    } else if (hasExtensions && request.path.startsWith("/extensions/pub/") && privateAppPublicRoutes.nonEmpty) {
      privateAppPublicRouter.find(request) match {
        case Some(route) if route.adminRoute.wantsBody  =>
          Some(actionBuilder.async(sourceBodyParser) { req => route.adminRoute.handle(route, req, req.body.some) })
        case Some(route) =>
          Some(actionBuilder.async { req => route.adminRoute.handle(route, req, None) })
        case None                                       => f
      }
    } else if (hasExtensions && request.path.startsWith("/extensions/") && privateAppAuthRoutes.nonEmpty) {
      privateAppAuthRouter.find(request) match {
        case Some(route) if route.adminRoute.wantsBody  =>
          Some(PrivateAppsAction.async(sourceBodyParser) { ctx =>
            route.adminRoute.handle(route, ctx.request, ctx.users, ctx.request.body.some)
          })
        case Some(route) =>
          Some(PrivateAppsAction.async { ctx => route.adminRoute.handle(route, ctx.request, ctx.users, None) })
        case None                                       => f
      }
    } else f
  }

  def resources(): Seq[Resource] = {
    if (hasExtensions) {
      entities.map(_.resource)
    } else {
      Seq.empty
    }
  }

  def publicKeys(): Future[Seq[PublicKeyJwk]] = {
    if (hasExtensions) {
      extensions.flatmapAsync { ext =>
        ext.publicKeys()
      }
    } else {
      Seq.empty.vfuture
    }
  }

  def start(): Unit = {
    if (hasExtensions) {
      extensions.foreach(_.start())
    } else {
      ()
    }
  }

  def stop(): Unit = {
    if (hasExtensions) {
      extensions.foreach(_.stop())
    } else {
      ()
    }
  }

  def syncStates(): Future[Unit] = {
    if (hasExtensions) {
      Source(extensions.toList)
        .mapAsync(1) { extension =>
          extension.syncStates()
        }
        .runWith(Sink.ignore)
        .map(_ => ())
    } else {
      ().vfuture
    }
  }

  def exportAllEntities(): Future[Map[String, Map[String, Seq[JsValue]]]] = {
    if (hasExtensions) {
      Source(entitiesMap.toList)
        .mapAsync(1) {
          case (group, ett) =>
            Source(ett.toList)
              .mapAsync(1) { entity =>
                entity.resource.access
                  .findAll(entity.resource.version.name)
                  .map(values => (entity.resource.pluralName, values))
              // entity.datastore.findAll().map(values => (entity.resource.pluralName, values))
              }
              .runFold((group, Map.empty[String, Seq[JsValue]])) { (tuple, elem) =>
                val newMap = tuple._2 + (elem._1 -> elem._2)
                (tuple._1, newMap)
              }
        }
        .runFold(Map.empty[String, Map[String, Seq[JsValue]]])((map, elem) => map + elem)
    } else {
      Map.empty[String, Map[String, Seq[JsValue]]].vfuture
    }
  }

  def importAllEntities(source: JsObject): Future[Unit] = {
    if (hasExtensions) {
      val extensions: Map[String, Map[String, Seq[JsValue]]] =
        source.asOpt[Map[String, Map[String, Seq[JsValue]]]].getOrElse(Map.empty[String, Map[String, Seq[JsValue]]])
      Source(
        extensions
          .view
          .mapValues(_.toSeq)
          .toSeq
          .flatMap { case (key, items) =>
            items.map(tuple => (key, tuple._1, tuple._2))
          }
          .toList
      ).mapAsync(1) {
        case (entityGroup, entityPluralName, entities) =>
          entitiesMap.get(entityGroup).flatMap(_.find(_.resource.pluralName == entityPluralName)) match {
            case None      => ().vfuture
            case Some(ent) =>
              Source(entities.toList)
                .mapAsync(1) { value =>
                  // env.datastores.rawDataStore.set(ent.datastore.key(value.select("id").asString), value.stringify.byteString, None)
                  env.datastores.rawDataStore.set(
                    ent.resource.access.key(value.select("id").asString),
                    value.stringify.byteString,
                    None
                  )
                }
                .runWith(Sink.ignore)
          }
          }.runWith(Sink.ignore)
        .map(_ => ())
    } else {
      ().vfuture
    }
  }

  def frontendExtensionsHtml(): Html = {
    Html(
      frontendExtensions
        .map(_.path)
        .map(p => s"""<script type=\"text/javascript\" src=\"$p\"></script>""")
        .mkString("\n")
    )
  }
}
