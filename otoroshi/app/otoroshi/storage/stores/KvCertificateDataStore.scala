package otoroshi.storage.stores

import org.apache.pekko.actor.Cancellable
import org.apache.pekko.stream.Materializer
import otoroshi.env.Env
import otoroshi.ssl.{Cert, CertificateDataStore, DynamicSSLEngineProvider}
import otoroshi.storage.{RedisLike, RedisLikeStore}
import otoroshi.utils
import otoroshi.utils.SchedulerHelper
import otoroshi.utils.letsencrypt.LetsEncryptHelper
import play.api.Logger
import play.api.libs.json.Format

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class KvCertificateDataStore(redisCli: RedisLike, _env: Env) extends CertificateDataStore with RedisLikeStore[Cert] {

  val logger: Logger = Logger("otoroshi-certificate-datastore")

  override def redisLike(implicit env: Env): RedisLike = redisCli
  override def fmt: Format[Cert]                       = Cert._fmt
  override def key(id: String): String                 = s"${_env.storageRoot}:certs:$id"
  override def extractId(value: Cert): String          = value.id

  val lastUpdatedKey: String = s"${_env.storageRoot}:certs-last-updated"

  val lastUpdatedRef        = new AtomicReference[String]("0")
  val includeJdkCaServerRef = new AtomicBoolean(true)
  val includeJdkCaClientRef = new AtomicBoolean(true)
  val lastTrustedCARef      = new AtomicReference[Seq[String]](Seq.empty)
  val cancelRef             = new AtomicReference[Cancellable](null)
  val cancelRenewRef        = new AtomicReference[Cancellable](null)
  val cancelCreateRef       = new AtomicReference[Cancellable](null)

  def startSync(): Unit = {
    implicit val ec: ExecutionContext = _env.otoroshiExecutionContext
    implicit val mat: Materializer = _env.otoroshiMaterializer
    implicit val env: Env = _env
    importInitialCerts(logger)
    cancelRenewRef.set(
      _env.otoroshiActorSystem.scheduler
        .scheduleAtFixedRate(60.seconds, 1.hour + ((Math.random() * 10) + 1).minutes)(SchedulerHelper.runnable {
          _env.datastores.certificatesDataStore.renewCertificates()
        })
    )
    cancelCreateRef.set(
      _env.otoroshiActorSystem.scheduler.scheduleAtFixedRate(60.seconds, 2.minutes)(utils.SchedulerHelper.runnable {
        LetsEncryptHelper.createFromServices()
        Cert.createFromServices()
      })
    )
    cancelRef.set(
      _env.otoroshiActorSystem.scheduler.scheduleAtFixedRate(2.seconds, 2.seconds)(utils.SchedulerHelper.runnable {
        for {
          // certs        <- findAll()
          last         <- redisCli.get(lastUpdatedKey).map(_.map(_.utf8String).getOrElse("0"))
          lastIcaServer =
              env.datastores.globalConfigDataStore.latestSafe.forall(_.tlsSettings.includeJdkCaServer)
          lastIcaClient =
              env.datastores.globalConfigDataStore.latestSafe.forall(_.tlsSettings.includeJdkCaClient)
          lastTrustedCA =
            env.datastores.globalConfigDataStore.latestSafe.map(_.tlsSettings.trustedCAsServer).getOrElse(Seq.empty)
        } yield {
          if (
            last != lastUpdatedRef.get()
            || lastIcaServer != includeJdkCaServerRef.get()
            || lastIcaClient != includeJdkCaClientRef.get()
            || lastTrustedCA != lastTrustedCARef.get()
          ) {
            lastUpdatedRef.set(last)
            includeJdkCaServerRef.set(lastIcaServer)
            includeJdkCaClientRef.set(lastIcaClient)
            lastTrustedCARef.set(lastTrustedCA)
            DynamicSSLEngineProvider.setCertificates(env)
          }
        }
      })
    )
  }

  def stopSync(): Unit = {
    Option(cancelCreateRef.get()).foreach(_.cancel())
    Option(cancelRenewRef.get()).foreach(_.cancel())
    Option(cancelRef.get()).foreach(_.cancel())
  }

  override def delete(id: String)(implicit ec: ExecutionContext, env: Env): Future[Boolean] =
    super.delete(id).andThen { case _ =>
      redisCli.set(lastUpdatedKey, System.currentTimeMillis().toString)
    }

  override def delete(value: Cert)(implicit ec: ExecutionContext, env: Env): Future[Boolean] =
    super.delete(value).andThen { case _ =>
      redisCli.set(lastUpdatedKey, System.currentTimeMillis().toString)
    }

  override def deleteAll()(implicit ec: ExecutionContext, env: Env): Future[Long] =
    super.deleteAll().andThen { case _ =>
      redisCli.set(lastUpdatedKey, System.currentTimeMillis().toString)
    }

  override def set(value: Cert, pxMilliseconds: Option[Duration] = None)(implicit
      ec: ExecutionContext,
      env: Env
  ): Future[Boolean] =
    super.set(value, pxMilliseconds).andThen { case _ =>
      redisCli.set(lastUpdatedKey, System.currentTimeMillis().toString)
    }

  override def exists(id: String)(implicit ec: ExecutionContext, env: Env): Future[Boolean] =
    super.exists(id).andThen { case _ =>
      redisCli.set(lastUpdatedKey, System.currentTimeMillis().toString)
    }

  override def exists(value: Cert)(implicit ec: ExecutionContext, env: Env): Future[Boolean] =
    super.exists(value).andThen { case _ =>
      redisCli.set(lastUpdatedKey, System.currentTimeMillis().toString)
    }
}
