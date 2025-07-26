package otoroshi.jobs.apikeys

import org.apache.pekko.stream.scaladsl.{Sink, Source}
import otoroshi.env.Env
import otoroshi.next.plugins.api.NgPluginCategory
import otoroshi.script._
import otoroshi.utils.syntax.implicits._
import play.api.Logger

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class ApikeysSecretsRotationJob extends Job {

  private val logger = Logger("otoroshi-apikeys-secrets-rotation-job")

  override def categories: Seq[NgPluginCategory] = Seq.empty

  override def uniqueId: JobId = JobId("io.otoroshi.core.jobs.ApikeysSecretsRotationJob")

  override def name: String = "Otoroshi apikeys secrets rotation job"

  override def jobVisibility: JobVisibility = JobVisibility.Internal

  override def kind: JobKind = JobKind.ScheduledEvery

  override def initialDelay(ctx: JobContext, env: Env): Option[FiniteDuration] = 1.minutes.some

  override def interval(ctx: JobContext, env: Env): Option[FiniteDuration] = 10.minutes.some

  override def starting: JobStarting = JobStarting.Automatically

  override def instantiation(ctx: JobContext, env: Env): JobInstantiation =
    JobInstantiation.OneInstancePerOtoroshiCluster

  override def predicate(ctx: JobContext, env: Env): Option[Boolean] = None

  override def jobRun(ctx: JobContext)(using env: Env, ec: ExecutionContext): Future[Unit] = {
    env.datastores.apiKeyDataStore.findAll().flatMap { apikeys =>
      Source(apikeys.toList)
        .mapAsync(1)(apikey => env.datastores.apiKeyDataStore.keyRotation(apikey))
        .runWith(Sink.seq)(using env.otoroshiMaterializer)
        .map(_ => ())
    }
  }
}
