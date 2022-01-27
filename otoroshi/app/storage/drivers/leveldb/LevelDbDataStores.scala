package otoroshi.storage.drivers.leveldb

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import otoroshi.auth.AuthConfigsDataStore
import otoroshi.cluster.{Cluster, ClusterStateDataStore, KvClusterStateDataStore}
import com.typesafe.config.ConfigFactory
import otoroshi.env.Env
import otoroshi.events.{AlertDataStore, AuditDataStore, HealthCheckDataStore}
import otoroshi.gateway.{InMemoryRequestsDataStore, RequestsDataStore}
import otoroshi.models._
import otoroshi.models._
import otoroshi.next.models.{KvNgRouteDataStore, KvNgRoutesCompositionDataStore, KvStoredNgBackendDataStore, KvStoredNgTargetDataStore, NgRouteDataStore, NgRoutesCompositionDataStore, StoredNgBackendDataStore, StoredNgTargetDataStore}
import otoroshi.script.{KvScriptDataStore, ScriptDataStore}
import play.api.inject.ApplicationLifecycle
import play.api.libs.json._
import play.api.{Configuration, Environment, Logger}
import otoroshi.ssl.{CertificateDataStore, ClientCertificateValidationDataStore, KvClientCertificateValidationDataStore}
import otoroshi.storage.stores._
import otoroshi.storage.{DataStoreHealth, DataStores, RawDataStore}
import otoroshi.tcp.{KvTcpServiceDataStoreDataStore, TcpServiceDataStore}
import otoroshi.storage.stores.{DataExporterConfigDataStore, KvRawDataStore, TeamDataStore, TenantDataStore}

import scala.concurrent.{ExecutionContext, Future}
import otoroshi.utils.syntax.implicits._

@deprecated(message = "Use FileDb instead", since = "1.5.0")
class LevelDbDataStores(
    configuration: Configuration,
    environment: Environment,
    lifecycle: ApplicationLifecycle,
    env: Env
) extends DataStores {

  lazy val logger = Logger("otoroshi-leveldb-datastores")

  logger.debug(s"path at $dbPath")

  lazy val dbPath: String       = configuration.getOptionalWithFileSupport[String]("app.leveldb.path").getOrElse("./leveldb")
  lazy val redisStatsItems: Int = configuration.getOptionalWithFileSupport[Int]("app.leveldb.windowSize").getOrElse(99)
  lazy val actorSystem          =
    ActorSystem(
      "otoroshi-leveldb-system",
      configuration
        .getOptionalWithFileSupport[Configuration]("app.actorsystems.datastore")
        .map(_.underlying)
        .getOrElse(ConfigFactory.empty)
    )
  lazy val redis                = new LevelDbRedis(actorSystem, dbPath)

  override def before(
      configuration: Configuration,
      environment: Environment,
      lifecycle: ApplicationLifecycle
  ): Future[Unit] = {
    logger.info("Now using LevelDB DataStores")
    logger.warn(s"LevelDB DataStores is deprecated and will be removed in a future release")
    redis.start()
    _serviceDescriptorDataStore.startCleanup(env)
    _certificateDataStore.startSync()
    FastFuture.successful(())
  }

  override def after(
      configuration: Configuration,
      environment: Environment,
      lifecycle: ApplicationLifecycle
  ): Future[Unit] = {
    _serviceDescriptorDataStore.stopCleanup()
    _certificateDataStore.stopSync()
    redis.stop()
    actorSystem.terminate()
    FastFuture.successful(())
  }

  private lazy val _privateAppsUserDataStore    = new KvPrivateAppsUserDataStore(redis, env)
  private lazy val _backOfficeUserDataStore     = new KvBackOfficeUserDataStore(redis, env)
  private lazy val _serviceGroupDataStore       = new KvServiceGroupDataStore(redis, env)
  private lazy val _globalConfigDataStore       = new KvGlobalConfigDataStore(redis, env)
  private lazy val _apiKeyDataStore             = new KvApiKeyDataStore(redis, env)
  private lazy val _serviceDescriptorDataStore  = new KvServiceDescriptorDataStore(redis, redisStatsItems, env)
  private lazy val _simpleAdminDataStore        = new KvSimpleAdminDataStore(redis, env)
  private lazy val _alertDataStore              = new KvAlertDataStore(redis)
  private lazy val _auditDataStore              = new KvAuditDataStore(redis)
  private lazy val _healthCheckDataStore        = new KvHealthCheckDataStore(redis, env)
  private lazy val _errorTemplateDataStore      = new KvErrorTemplateDataStore(redis, env)
  private lazy val _requestsDataStore           = new InMemoryRequestsDataStore()
  private lazy val _canaryDataStore             = new KvCanaryDataStore(redis, env)
  private lazy val _chaosDataStore              = new KvChaosDataStore(redis, env)
  private lazy val _jwtVerifDataStore           = new KvGlobalJwtVerifierDataStore(redis, env)
  private lazy val _globalOAuth2ConfigDataStore = new KvAuthConfigsDataStore(redis, env)
  private lazy val _certificateDataStore        = new KvCertificateDataStore(redis, env)

  private lazy val _clusterStateDataStore                   = new KvClusterStateDataStore(redis, env)
  override def clusterStateDataStore: ClusterStateDataStore = _clusterStateDataStore

  private lazy val _clientCertificateValidationDataStore                                  = new KvClientCertificateValidationDataStore(redis, env)
  override def clientCertificateValidationDataStore: ClientCertificateValidationDataStore =
    _clientCertificateValidationDataStore

  private lazy val _scriptDataStore             = new KvScriptDataStore(redis, env)
  override def scriptDataStore: ScriptDataStore = _scriptDataStore

  private lazy val _tcpServiceDataStore                 = new KvTcpServiceDataStoreDataStore(redis, env)
  override def tcpServiceDataStore: TcpServiceDataStore = _tcpServiceDataStore

  private lazy val _rawDataStore          = new KvRawDataStore(redis)
  override def rawDataStore: RawDataStore = _rawDataStore

  private lazy val _webAuthnAdminDataStore                    = new KvWebAuthnAdminDataStore()
  override def webAuthnAdminDataStore: WebAuthnAdminDataStore = _webAuthnAdminDataStore

  private lazy val _webAuthnRegistrationsDataStore                            = new WebAuthnRegistrationsDataStore()
  override def webAuthnRegistrationsDataStore: WebAuthnRegistrationsDataStore = _webAuthnRegistrationsDataStore

  private lazy val _tenantDataStore             = new TenantDataStore(redis, env)
  override def tenantDataStore: TenantDataStore = _tenantDataStore

  private lazy val _teamDataStore           = new TeamDataStore(redis, env)
  override def teamDataStore: TeamDataStore = _teamDataStore

  private lazy val _dataExporterConfigDataStore                         = new DataExporterConfigDataStore(redis, env)
  override def dataExporterConfigDataStore: DataExporterConfigDataStore = _dataExporterConfigDataStore

  private lazy val _routeDataStore = new KvNgRouteDataStore(redis, env)
  override def routeDataStore: NgRouteDataStore = _routeDataStore

  private lazy val _routesCompositionDataStore = new KvNgRoutesCompositionDataStore(redis, env)
  override def routesCompositionDataStore: NgRoutesCompositionDataStore = _routesCompositionDataStore

  private lazy val _targetsDataStore = new KvStoredNgTargetDataStore(redis, env)
  override def targetsDataStore: StoredNgTargetDataStore = _targetsDataStore

  private lazy val _backendsDataStore = new KvStoredNgBackendDataStore(redis, env)
  override def backendsDataStore: StoredNgBackendDataStore = _backendsDataStore

  override def privateAppsUserDataStore: PrivateAppsUserDataStore               = _privateAppsUserDataStore
  override def backOfficeUserDataStore: BackOfficeUserDataStore                 = _backOfficeUserDataStore
  override def serviceGroupDataStore: ServiceGroupDataStore                     = _serviceGroupDataStore
  override def globalConfigDataStore: GlobalConfigDataStore                     = _globalConfigDataStore
  override def apiKeyDataStore: ApiKeyDataStore                                 = _apiKeyDataStore
  override def serviceDescriptorDataStore: ServiceDescriptorDataStore           = _serviceDescriptorDataStore
  override def simpleAdminDataStore: SimpleAdminDataStore                       = _simpleAdminDataStore
  override def alertDataStore: AlertDataStore                                   = _alertDataStore
  override def auditDataStore: AuditDataStore                                   = _auditDataStore
  override def healthCheckDataStore: HealthCheckDataStore                       = _healthCheckDataStore
  override def errorTemplateDataStore: ErrorTemplateDataStore                   = _errorTemplateDataStore
  override def requestsDataStore: RequestsDataStore                             = _requestsDataStore
  override def canaryDataStore: CanaryDataStore                                 = _canaryDataStore
  override def health()(implicit ec: ExecutionContext): Future[DataStoreHealth] = redis.health()(ec)
  override def chaosDataStore: ChaosDataStore                                   = _chaosDataStore
  override def globalJwtVerifierDataStore: GlobalJwtVerifierDataStore           = _jwtVerifDataStore
  override def certificatesDataStore: CertificateDataStore                      = _certificateDataStore
  override def authConfigsDataStore: AuthConfigsDataStore                       = _globalOAuth2ConfigDataStore
  override def rawExport(
      group: Int
  )(implicit ec: ExecutionContext, mat: Materializer, env: Env): Source[JsValue, NotUsed] = {
    Source
      .future(
        redis.keys(s"${env.storageRoot}:*")
      )
      .mapConcat(_.toList)
      .grouped(group)
      .mapAsync(1) {
        case keys if keys.isEmpty => FastFuture.successful(Seq.empty[JsValue])
        case keys                 => {
          Future.sequence(
            keys
              .filterNot { key =>
                Cluster.filteredKey(key, env)
              //key == s"${env.storageRoot}:cluster:" ||
              //key == s"${env.storageRoot}:events:audit" ||
              //key == s"${env.storageRoot}:events:alerts" ||
              //key.startsWith(s"${env.storageRoot}:users:backoffice") ||
              //key.startsWith(s"${env.storageRoot}:admins:") ||
              //key.startsWith(s"${env.storageRoot}:u2f:users:") ||
              //// key.startsWith(s"${env.storageRoot}:users:") ||
              //key.startsWith(s"${env.storageRoot}:webauthn:admins:") ||
              //key.startsWith(s"${env.storageRoot}:deschealthcheck:") ||
              //key.startsWith(s"${env.storageRoot}:scall:stats:") ||
              //key.startsWith(s"${env.storageRoot}:scalldur:stats:") ||
              //key.startsWith(s"${env.storageRoot}:scallover:stats:") ||
              //(key.startsWith(s"${env.storageRoot}:data:") && key.endsWith(":stats:in")) ||
              //(key.startsWith(s"${env.storageRoot}:data:") && key.endsWith(":stats:out"))
              }
              .map { key =>
                redis.get(key).flatMap {
                  case None        => FastFuture.successful(JsNull)
                  case Some(value) => {
                    val (what, jsonValue) = toJson(value)
                    redis.pttl(key).map { ttl =>
                      Json.obj(
                        "k" -> key,
                        "v" -> jsonValue,
                        "t" -> (if (ttl == -1) -1 else (System.currentTimeMillis() + ttl)),
                        "w" -> what
                      )
                    }
                  }
                }
              }
          )
        }
      }
      .map(_.filterNot(_ == JsNull))
      .mapConcat(_.toList)
  }

  override def fullNdJsonExport(group: Int, groupWorkers: Int, keyWorkers: Int): Future[Source[JsValue, _]] = {

    implicit val ev  = env
    implicit val ecc = env.otoroshiExecutionContext
    implicit val mat = env.otoroshiMaterializer

    FastFuture.successful(
      Source
        .future(redis.keys(s"${env.storageRoot}:*"))
        .mapConcat(_.toList)
        .grouped(10)
        .mapAsync(1) {
          case keys if keys.isEmpty => FastFuture.successful(Seq.empty[JsValue])
          case keys                 => {
            Source(keys.toList)
              .mapAsync(1) { key =>
                redis.rawGet(key).flatMap {
                  case None        => FastFuture.successful(JsNull)
                  case Some(value) => {
                    toJson(value) match {
                      case (_, JsNull)       => FastFuture.successful(JsNull)
                      case (what, jsonValue) =>
                        redis.pttl(key).map { ttl =>
                          Json.obj(
                            "k" -> key,
                            "v" -> jsonValue,
                            "t" -> (if (ttl == -1) -1 else (System.currentTimeMillis() + ttl)),
                            "w" -> what
                          )
                        }
                    }
                  }
                }
              }
              .runWith(Sink.seq)
              .map(_.filterNot(_ == JsNull))
          }
        }
        .mapConcat(_.toList)
    )
  }

  override def fullNdJsonImport(export: Source[JsValue, _]): Future[Unit] = {

    implicit val ev  = env
    implicit val ecc = env.otoroshiExecutionContext
    implicit val mat = env.otoroshiMaterializer

    redis
      .keys(s"${env.storageRoot}:*")
      .flatMap(keys => if (keys.nonEmpty) redis.del(keys: _*) else FastFuture.successful(0L))
      .flatMap { _ =>
        export
          .mapAsync(1) { json =>
            val key   = (json \ "k").as[String]
            val value = (json \ "v").as[JsValue]
            val pttl  = (json \ "t").as[Long]
            val what  = (json \ "w").as[String]
            (what match {
              case "counter" => redis.set(key, value.as[Long].toString)
              case "string"  => redis.set(key, value.as[String])
              case "hash"    =>
                Source(value.as[JsObject].value.toList)
                  .mapAsync(1)(v => redis.hset(key, v._1, Json.stringify(v._2)))
                  .runWith(Sink.ignore)
              case "list"    => redis.lpush(key, value.as[JsArray].value.map(Json.stringify): _*)
              case "set"     => redis.sadd(key, value.as[JsArray].value.map(Json.stringify): _*)
              case _         => FastFuture.successful(0L)
            }).flatMap { _ =>
              if (pttl > -1L) {
                redis.pexpire(key, pttl)
              } else {
                FastFuture.successful(true)
              }
            }
          }
          .runWith(Sink.ignore)
          .map(_ => ())
      }
  }

  private def toJson(value: Any): (String, JsValue) = {

    def strToTuple(str: String): (String, JsValue) = {
      val parts = str.split("<#>")
      (parts(0), JsString(parts(1)))
    }

    value match {
      case str: ByteString if str.containsSlice(ByteString("<#>")) =>
        ("hash", JsObject(str.utf8String.split(";;;").map(strToTuple).toSeq))
      case str: ByteString if str.containsSlice(ByteString(";;;")) =>
        ("list", JsArray(str.utf8String.split(";;;").toSeq.map(JsString.apply)))
      case str: ByteString if str.containsSlice(ByteString(";;>")) =>
        ("set", JsArray(str.utf8String.split(";;>").toSeq.map(JsString.apply)))
      case str: ByteString                                         => ("string", JsString(str.utf8String))
      case e                                                       => throw new RuntimeException(s"Unkown type for ${value}")
    }
  }
}
