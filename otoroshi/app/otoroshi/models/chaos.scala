package otoroshi.models

import java.util.concurrent.TimeUnit

import otoroshi.env.Env
import otoroshi.models.SnowMonkeyConfig.logger
import org.joda.time.{DateTime, LocalTime}
import play.api.Logger
import play.api.libs.json._

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class BadResponse(status: Int, body: String, headers: Map[String, String]) {
  def asJson: JsValue = BadResponse.fmt.writes(this)
}

object BadResponse {
  val fmt: Format[BadResponse] = new Format[BadResponse] {
    override def reads(json: JsValue): JsResult[BadResponse] =
      Try {
        JsSuccess(
          BadResponse(
            status = (json \ "status").asOpt[Int].orElse((json \ "status").asOpt[String].map(_.toInt)).getOrElse(500),
            body = (json \ "body").asOpt[String].getOrElse("""{"error":"..."}"""),
            headers = (json \ "headers").asOpt[Map[String, String]].getOrElse(Map.empty[String, String])
          )
        )
      } recover { case t =>
        JsError(t.getMessage)
      } get
    override def writes(o: BadResponse): JsValue             =
      Json.obj(
        "status"  -> o.status,
        "body"    -> o.body,
        "headers" -> o.headers
      )
  }
}

sealed trait FaultConfig {
  def ratio: Double
  def asJson: JsValue
}
case class LargeRequestFaultConfig(ratio: Double, additionalRequestSize: Int)                   extends FaultConfig {
  def asJson: JsValue = LargeRequestFaultConfig.fmt.writes(this)
}
object LargeRequestFaultConfig {
  val fmt: Format[LargeRequestFaultConfig] = new Format[LargeRequestFaultConfig] {
    override def reads(json: JsValue): JsResult[LargeRequestFaultConfig] =
      Try {
        JsSuccess(
          //LargeRequestFaultConfig(
          //  ratio = (json \ "ratio").asOpt[Double].getOrElse(0.2),
          //  additionalRequestSize = (json \ "additionalRequestSize").asOpt[Int].getOrElse(0)
          //)
          LargeRequestFaultConfig(
            ratio = (json \ "ratio").as[Double],
            additionalRequestSize = (json \ "additionalRequestSize").as[Int]
          )
        )
      } recover { case t =>
        JsError(t.getMessage)
      } get
    override def writes(o: LargeRequestFaultConfig): JsValue             =
      Json.obj(
        "ratio"                 -> o.ratio,
        "additionalRequestSize" -> o.additionalRequestSize
      )
  }
}
case class LargeResponseFaultConfig(ratio: Double, additionalResponseSize: Int)                 extends FaultConfig {
  def asJson: JsValue = LargeResponseFaultConfig.fmt.writes(this)
}
object LargeResponseFaultConfig {
  val fmt: Format[LargeResponseFaultConfig] = new Format[LargeResponseFaultConfig] {
    override def reads(json: JsValue): JsResult[LargeResponseFaultConfig] =
      Try {
        JsSuccess(
          // LargeResponseFaultConfig(
          //   ratio = (json \ "ratio").asOpt[Double].getOrElse(0.2),
          //   additionalResponseSize = (json \ "additionalResponseSize").asOpt[Int].getOrElse(0)
          // )
          LargeResponseFaultConfig(
            ratio = (json \ "ratio").as[Double],
            additionalResponseSize = (json \ "additionalResponseSize").as[Int]
          )
        )
      } recover { case t =>
        JsError(t.getMessage)
      } get
    override def writes(o: LargeResponseFaultConfig): JsValue             =
      Json.obj(
        "ratio"                  -> o.ratio,
        "additionalResponseSize" -> o.additionalResponseSize
      )
  }
}
case class LatencyInjectionFaultConfig(ratio: Double, from: FiniteDuration, to: FiniteDuration) extends FaultConfig {
  def asJson: JsValue = LatencyInjectionFaultConfig.fmt.writes(this)
}
object LatencyInjectionFaultConfig {
  val fmt: Format[LatencyInjectionFaultConfig] = new Format[LatencyInjectionFaultConfig] {
    override def reads(json: JsValue): JsResult[LatencyInjectionFaultConfig] =
      Try {
        JsSuccess(
          // LatencyInjectionFaultConfig(
          //   ratio = (json \ "ratio").asOpt[Double].getOrElse(0.2),
          //   from =
          //     (json \ "from").asOpt(using SnowMonkeyConfig.durationFmt).getOrElse(FiniteDuration(0, TimeUnit.MILLISECONDS)),
          //   to = (json \ "to").asOpt(using SnowMonkeyConfig.durationFmt).getOrElse(FiniteDuration(0, TimeUnit.MILLISECONDS))
          // )
          LatencyInjectionFaultConfig(
            ratio = (json \ "ratio").as[Double],
            from = (json \ "from").as(using SnowMonkeyConfig.durationFmt),
            to = (json \ "to").as(using SnowMonkeyConfig.durationFmt)
          )
        )
      } recover { case t =>
        JsError(t.getMessage)
      } get
    override def writes(o: LatencyInjectionFaultConfig): JsValue             =
      Json.obj(
        "ratio" -> o.ratio,
        "from"  -> SnowMonkeyConfig.durationFmt.writes(o.from),
        "to"    -> SnowMonkeyConfig.durationFmt.writes(o.to)
      )
  }
}
case class BadResponsesFaultConfig(ratio: Double, responses: Seq[BadResponse])                  extends FaultConfig {
  def asJson: JsValue = BadResponsesFaultConfig.fmt.writes(this)
}
object BadResponsesFaultConfig {
  val fmt: Format[BadResponsesFaultConfig] = new Format[BadResponsesFaultConfig] {
    override def reads(json: JsValue): JsResult[BadResponsesFaultConfig] =
      Try {
        JsSuccess(
          // BadResponsesFaultConfig(
          //   ratio = (json \ "ratio").asOpt[Double].getOrElse(0.2),
          //   responses = (json \ "responses").asOpt(using Reads.seq(using BadResponse.fmt)).getOrElse(Seq.empty)
          // )
          BadResponsesFaultConfig(
            ratio = (json \ "ratio").as[Double],
            responses = (json \ "responses").as(using Reads.seq(using BadResponse.fmt))
          )
        )
      } recover { case t =>
        JsError(t.getMessage)
      } get
    override def writes(o: BadResponsesFaultConfig): JsValue             =
      Json.obj(
        "ratio"     -> o.ratio,
        "responses" -> JsArray(o.responses.map(_.asJson))
      )
  }
}

case class ChaosConfig(
    enabled: Boolean = false,
    largeRequestFaultConfig: Option[LargeRequestFaultConfig] = None,
    largeResponseFaultConfig: Option[LargeResponseFaultConfig] = None,
    latencyInjectionFaultConfig: Option[LatencyInjectionFaultConfig] = None,
    badResponsesFaultConfig: Option[BadResponsesFaultConfig] = None
) {
  def asJson: JsValue = ChaosConfig._fmt.writes(this)
}

object ChaosConfig {
  val _fmt: Format[ChaosConfig] = new Format[ChaosConfig] {
    override def reads(json: JsValue): JsResult[ChaosConfig] = {
      Try {
        ChaosConfig(
          enabled = (json \ "enabled").asOpt[Boolean].getOrElse(false),
          largeRequestFaultConfig =
            (json \ "largeRequestFaultConfig").asOpt[LargeRequestFaultConfig](using LargeRequestFaultConfig.fmt),
          largeResponseFaultConfig =
            (json \ "largeResponseFaultConfig").asOpt[LargeResponseFaultConfig](using LargeResponseFaultConfig.fmt),
          latencyInjectionFaultConfig =
            (json \ "latencyInjectionFaultConfig").asOpt[LatencyInjectionFaultConfig](using LatencyInjectionFaultConfig.fmt),
          badResponsesFaultConfig =
            (json \ "badResponsesFaultConfig").asOpt[BadResponsesFaultConfig](using BadResponsesFaultConfig.fmt)
        )
      } map { case sd =>
        JsSuccess(sd)
      } recover { case t =>
        logger.error("Error while reading SnowMonkeyConfig", t)
        JsError(t.getMessage)
      } get
    }
    override def writes(o: ChaosConfig): JsValue = {
      Json.obj(
        "enabled"                     -> o.enabled,
        "largeRequestFaultConfig"     -> o.largeRequestFaultConfig.map(_.asJson).getOrElse(JsNull).as[JsValue],
        "largeResponseFaultConfig"    -> o.largeResponseFaultConfig.map(_.asJson).getOrElse(JsNull).as[JsValue],
        "latencyInjectionFaultConfig" -> o.latencyInjectionFaultConfig.map(_.asJson).getOrElse(JsNull).as[JsValue],
        "badResponsesFaultConfig"     -> o.badResponsesFaultConfig.map(_.asJson).getOrElse(JsNull).as[JsValue]
      )
    }
  }
}

sealed trait OutageStrategy
case object OneServicePerGroup  extends OutageStrategy
case object AllServicesPerGroup extends OutageStrategy

case class SnowMonkeyConfig(
    enabled: Boolean = false,
    outageStrategy: OutageStrategy = OneServicePerGroup,
    includeUserFacingDescriptors: Boolean = false,
    dryRun: Boolean = false,
    timesPerDay: Int = 1,
    startTime: LocalTime = LocalTime.parse("09:00:00"),
    stopTime: LocalTime = LocalTime.parse("23:59:59"),
    outageDurationFrom: FiniteDuration = FiniteDuration(10, TimeUnit.MINUTES),
    outageDurationTo: FiniteDuration = FiniteDuration(60, TimeUnit.MINUTES),
    targetGroups: Seq[String] = Seq.empty,
    chaosConfig: ChaosConfig = ChaosConfig(
      enabled = true,
      largeRequestFaultConfig = None,
      largeResponseFaultConfig = None,
      latencyInjectionFaultConfig = Some(LatencyInjectionFaultConfig(0.2, 500.millis, 5000.millis)),
      badResponsesFaultConfig = Some(
        BadResponsesFaultConfig(
          0.2,
          Seq(
            BadResponse(
              502,
              """{"error":"Nihonzaru everywhere ..."}""",
              headers = Map("Content-Type" -> "application/json")
            )
          )
        )
      )
    )
) {
  def asJson: JsValue                                                  = SnowMonkeyConfig._fmt.writes(this)
  def save()(using ec: ExecutionContext, env: Env): Future[Boolean] =
    env.datastores.globalConfigDataStore.singleton().flatMap { conf =>
      conf.copy(snowMonkeyConfig = this).save()
    }
}

object SnowMonkeyConfig {

  lazy val logger: Logger = Logger("otoroshi-snowmonkey-config")

  val durationFmt: Format[FiniteDuration] = new Format[FiniteDuration] {
    override def reads(json: JsValue): JsResult[FiniteDuration] =
      json
        .asOpt[Long]
        .map(l => JsSuccess(FiniteDuration(l, TimeUnit.MILLISECONDS)))
        .getOrElse(JsError("Not a valid duration"))
    override def writes(o: FiniteDuration): JsValue             = JsNumber(o.toMillis)
  }

  val outageStrategyFmt: Format[OutageStrategy] = new Format[OutageStrategy] {
    override def reads(json: JsValue): JsResult[OutageStrategy] =
      json
        .asOpt[String]
        .map {
          case "OneServicePerGroup"  => JsSuccess(OneServicePerGroup)
          case "AllServicesPerGroup" => JsSuccess(AllServicesPerGroup)
          case _                     => JsSuccess(OneServicePerGroup)
        }
        .getOrElse(JsSuccess(OneServicePerGroup))
    override def writes(o: OutageStrategy): JsValue             =
      o match {
        case OneServicePerGroup  => JsString("OneServicePerGroup")
        case AllServicesPerGroup => JsString("AllServicesPerGroup")
      }
  }

  val _fmt: Format[SnowMonkeyConfig] = new Format[SnowMonkeyConfig] {

    override def writes(o: SnowMonkeyConfig): JsValue = {
      Json.obj(
        "enabled"                      -> o.enabled,
        "outageStrategy"               -> outageStrategyFmt.writes(o.outageStrategy),
        "includeUserFacingDescriptors" -> o.includeUserFacingDescriptors,
        "dryRun"                       -> o.dryRun,
        "timesPerDay"                  -> o.timesPerDay,
        "startTime"                    -> play.api.libs.json.JodaWrites.DefaultJodaLocalTimeWrites.writes(o.startTime),
        "stopTime"                     -> play.api.libs.json.JodaWrites.DefaultJodaLocalTimeWrites.writes(o.stopTime),
        "outageDurationFrom"           -> durationFmt.writes(o.outageDurationFrom),
        "outageDurationTo"             -> durationFmt.writes(o.outageDurationTo),
        "targetGroups"                 -> JsArray(o.targetGroups.map(JsString.apply)),
        "chaosConfig"                  -> ChaosConfig._fmt.writes(o.chaosConfig)
      )
    }

    override def reads(json: JsValue): JsResult[SnowMonkeyConfig] = {
      Try {
        SnowMonkeyConfig(
          enabled = (json \ "enabled").asOpt[Boolean].getOrElse(false),
          outageStrategy =
            (json \ "outageStrategy").asOpt[OutageStrategy](using outageStrategyFmt).getOrElse(OneServicePerGroup),
          includeUserFacingDescriptors = (json \ "includeUserFacingDescriptors").asOpt[Boolean].getOrElse(false),
          dryRun = (json \ "dryRun").asOpt[Boolean].getOrElse(false),
          timesPerDay = (json \ "timesPerDay").asOpt[Int].getOrElse(1),
          startTime = (json \ "startTime")
            .asOpt[LocalTime](using play.api.libs.json.JodaReads.DefaultJodaLocalTimeReads)
            .getOrElse(LocalTime.parse("09:00:00")),
          stopTime = (json \ "stopTime")
            .asOpt[LocalTime](using play.api.libs.json.JodaReads.DefaultJodaLocalTimeReads)
            .getOrElse(LocalTime.parse("23:59:59")),
          outageDurationFrom = (json \ "outageDurationFrom")
            .asOpt[FiniteDuration](using durationFmt)
            .getOrElse(FiniteDuration(1, TimeUnit.HOURS)),
          outageDurationTo = (json \ "outageDurationTo")
            .asOpt[FiniteDuration](using durationFmt)
            .getOrElse(FiniteDuration(10, TimeUnit.MINUTES)),
          targetGroups = (json \ "targetGroups").asOpt[Seq[String]].getOrElse(Seq.empty),
          chaosConfig = (json \ "chaosConfig")
            .asOpt[ChaosConfig](using ChaosConfig._fmt)
            .getOrElse(ChaosConfig(enabled = true, None, None, None, None))
        )
      } map { case sd =>
        JsSuccess(sd)
      } recover { case t =>
        logger.error("Error while reading SnowMonkeyConfig", t)
        JsError(t.getMessage)
      } get
    }
  }

  def toJson(value: SnowMonkeyConfig): JsValue                 = _fmt.writes(value)
  def fromJsons(value: JsValue): SnowMonkeyConfig              =
    try {
      _fmt.reads(value).get
    } catch {
      case e: Throwable =>
        logger.error(s"Try to deserialize ${Json.prettyPrint(value)}")
        throw e
    }
  def fromJsonSafe(value: JsValue): JsResult[SnowMonkeyConfig] = _fmt.reads(value)
}

case class Outage(
    descriptorId: String,
    descriptorName: String,
    startedAt: DateTime,
    until: LocalTime,
    duration: FiniteDuration
) {
  def asJson: JsValue = Outage.fmt.writes(this)
}

object Outage {
  val fmt: Format[Outage] = new Format[Outage] {
    override def writes(o: Outage)    =
      Json.obj(
        "descriptorId"   -> o.descriptorId,
        "descriptorName" -> o.descriptorName,
        "until"          -> o.until.toString(),
        "duration"       -> o.duration.toMillis,
        "startedAt"      -> o.startedAt.toString
      )
    override def reads(json: JsValue) =
      Try {
        JsSuccess(
          Outage(
            descriptorId = (json \ "descriptorId").asOpt[String].getOrElse("--"),
            descriptorName = (json \ "descriptorName").asOpt[String].getOrElse("--"),
            until = (json \ "until").asOpt[String].map(v => LocalTime.parse(v)).getOrElse(DateTime.now().toLocalTime),
            duration = (json \ "duration").asOpt[Long].map(v => v.millis).getOrElse(0.millis),
            startedAt = (json \ "startedAt").asOpt[String].map(v => DateTime.parse(v)).getOrElse(DateTime.now())
          )
        )
      } recover { case e =>
        JsError(e.getMessage)
      } get
  }
}

trait ChaosDataStore {
  def serviceAlreadyOutage(serviceId: String)(using ec: ExecutionContext, env: Env): Future[Boolean]
  def serviceOutages(serviceId: String)(using ec: ExecutionContext, env: Env): Future[Int]
  def groupOutages(groupId: String)(using ec: ExecutionContext, env: Env): Future[Int]
  def registerOutage(descriptor: ServiceDescriptor, conf: SnowMonkeyConfig)(using
      ec: ExecutionContext,
      env: Env
  ): Future[FiniteDuration]
  def resetOutages()(using ec: ExecutionContext, env: Env): Future[Unit]
  def startSnowMonkey()(using ec: ExecutionContext, env: Env): Future[Unit]
  def stopSnowMonkey()(using ec: ExecutionContext, env: Env): Future[Unit]
  def getOutages()(using ec: ExecutionContext, env: Env): Future[Seq[Outage]]
}
