package otoroshi.events

import scala.concurrent.{Await, Future, Promise}
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer._
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}
import akka.Done
import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.util.FastFuture._
import akka.http.scaladsl.util.FastFuture
import akka.kafka.ProducerSettings
import akka.stream.scaladsl.{Sink, Source}
import org.apache.kafka.common.config.SslConfigs
import play.api.libs.json._
import otoroshi.env.Env
import otoroshi.models.Exporter
import org.apache.kafka.common.config.internals.BrokerSecurityConfigs
import otoroshi.models.Exporter
import otoroshi.utils.http.MtlsConfig
import otoroshi.ssl.DynamicSSLEngineProvider
import otoroshi.utils.syntax.implicits._

case class KafkaConfig(
    servers: Seq[String],
    keyPass: Option[String] = None,
    keystore: Option[String] = None,
    truststore: Option[String] = None,
    sendEvents: Boolean = false,
    hostValidation: Boolean = true,
    topic: String = "otoroshi-events",
    mtlsConfig: MtlsConfig = MtlsConfig()
) extends Exporter {
  def json: JsValue   = KafkaConfig.format.writes(this)
  def toJson: JsValue = KafkaConfig.format.writes(this)
}

object KafkaConfig {

  implicit val format = new Format[KafkaConfig] { // Json.format[KafkaConfig]

    override def writes(o: KafkaConfig): JsValue =
      Json.obj(
        "servers"    -> JsArray(o.servers.map(JsString.apply)),
        "keyPass"    -> o.keyPass.map(JsString.apply).getOrElse(JsNull).as[JsValue],
        "keystore"   -> o.keystore.map(JsString.apply).getOrElse(JsNull).as[JsValue],
        "truststore" -> o.truststore.map(JsString.apply).getOrElse(JsNull).as[JsValue],
        "topic"      -> o.topic,
        "sendEvents" -> o.sendEvents,
        "hostValidation" -> o.hostValidation,
        "mtlsConfig" -> o.mtlsConfig.json
      )

    override def reads(json: JsValue): JsResult[KafkaConfig] =
      Try {
        KafkaConfig(
          servers = (json \ "servers").asOpt[Seq[String]].getOrElse(Seq.empty),
          keyPass = (json \ "keyPass").asOpt[String],
          keystore = (json \ "keystore").asOpt[String],
          truststore = (json \ "truststore").asOpt[String],
          sendEvents = (json \ "sendEvents").asOpt[Boolean].getOrElse(false),
          hostValidation = (json \ "hostValidation").asOpt[Boolean].getOrElse(true),
          topic = (json \ "topic").asOpt[String].getOrElse("otoroshi-events"),
          mtlsConfig = MtlsConfig.read((json \ "mtlsConfig").asOpt[JsValue])
        )
      } match {
        case Failure(e)  => JsError(e.getMessage)
        case Success(kc) => JsSuccess(kc)
      }
  }
}

object KafkaSettings {

  import scala.concurrent.duration._

  def waitForFirstSetup(env: Env): Future[Unit] = {
    Source
      .tick(0.second, 1.second, ())
      .filter(_ => DynamicSSLEngineProvider.isFirstSetupDone)
      .take(1)
      .runWith(Sink.head)(env.otoroshiMaterializer)
  }

  def producerSettings(_env: otoroshi.env.Env, config: KafkaConfig): ProducerSettings[Array[Byte], String] = {

    val settings = ProducerSettings
      .create(_env.analyticsActorSystem, new ByteArraySerializer(), new StringSerializer())
      .withBootstrapServers(config.servers.mkString(","))

    if (config.mtlsConfig.mtls) {
      // AWAIT: valid
      Await.result(waitForFirstSetup(_env), 5.seconds) // wait until certs fully populated at least once
      val (jks1, jks2, password) = config.mtlsConfig.toJKS(_env)
      settings
        .withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
        .withProperty(BrokerSecurityConfigs.SSL_CLIENT_AUTH_CONFIG, "required")
        .withProperty(SslConfigs.SSL_KEY_PASSWORD_CONFIG, "")
        .withProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, jks1.getAbsolutePath)
        .withProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, password)
        .withProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, jks2.getAbsolutePath)
        .withProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, password)
        .applyOnIf(!config.hostValidation)(_.withProperty(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, ""))
    } else {
      val s = for {
        ks <- config.keystore
        ts <- config.truststore
        kp <- config.keyPass
      } yield {
        settings
          .withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
          .withProperty(BrokerSecurityConfigs.SSL_CLIENT_AUTH_CONFIG, "required")
          .withProperty(SslConfigs.SSL_KEY_PASSWORD_CONFIG, kp)
          .withProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, ks)
          .withProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, kp)
          .withProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, ts)
          .withProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, kp)
          .applyOnIf(!config.hostValidation)(_.withProperty(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, ""))
      }
      s.getOrElse(settings)
    }
  }
}

case class KafkaWrapperEvent(event: JsValue, env: Env, config: KafkaConfig)
case class KafkaWrapperEventClose()

class KafkaWrapper(actorSystem: ActorSystem, env: Env, topicFunction: KafkaConfig => String) {

  val kafkaWrapperActor = actorSystem.actorOf(KafkaWrapperActor.props(env, topicFunction))

  def publish(event: JsValue, forcePush: Boolean = false)(env: Env, config: KafkaConfig): Future[Done] = {
    kafkaWrapperActor ! KafkaWrapperEvent(event, env, if (forcePush) config.copy(sendEvents = true) else config)
    FastFuture.successful(Done)
  }

  def close(): Unit = {
    kafkaWrapperActor ! KafkaWrapperEventClose()
  }
}

class KafkaWrapperActor(env: Env, topicFunction: KafkaConfig => String) extends Actor {

  implicit val ec = env.analyticsExecutionContext

  var config: Option[KafkaConfig]               = None
  var eventProducer: Option[KafkaEventProducer] = None

  lazy val logger = play.api.Logger("otoroshi-kafka-wrapper")

  override def receive: Receive = {
    case event: KafkaWrapperEvent if config.isEmpty && eventProducer.isEmpty                                   => {
      config = Some(event.config)
      eventProducer.foreach(_.close())
      eventProducer = Some(new KafkaEventProducer(event.env, event.config, topicFunction))
      if (event.config.sendEvents) {
        eventProducer.get.publish(event.event).andThen { case Failure(e) =>
          logger.error("Error while pushing event to kafka", e)
        }
      }
    }
    case event: KafkaWrapperEvent if config.isDefined && config.get != event.config && event.config.sendEvents => {
      config = Some(event.config)
      eventProducer.foreach(_.close())
      eventProducer = Some(new KafkaEventProducer(event.env, event.config, topicFunction))
      if (event.config.sendEvents) {
        eventProducer.get.publish(event.event).andThen { case Failure(e) =>
          logger.error("Error while pushing event to kafka", e)
        }
      }
    }
    case event: KafkaWrapperEvent                                                                              =>
      if (event.config.sendEvents) {
        eventProducer.get.publish(event.event).andThen { case Failure(e) =>
          logger.error("Error while pushing event to kafka", e)
        }
      }
    case KafkaWrapperEventClose()                                                                              =>
      eventProducer.foreach(_.close())
      config = None
      eventProducer = None
    case _                                                                                                     =>
  }
}

object KafkaWrapperActor {
  def props(env: Env, topicFunction: KafkaConfig => String) = Props(new KafkaWrapperActor(env, topicFunction))
}

class KafkaEventProducer(_env: otoroshi.env.Env, config: KafkaConfig, topicFunction: KafkaConfig => String) {

  implicit val ec = _env.analyticsExecutionContext

  lazy val logger = play.api.Logger("otoroshi-kafka-connector")

  lazy val topic = topicFunction(config)

  logger.debug(s"Initializing kafka event store on topic ${topic}")

  private lazy val producerSettings                        = KafkaSettings.producerSettings(_env, config)
  private lazy val producer: Producer[Array[Byte], String] = producerSettings.createKafkaProducer

  def publish(event: JsValue): Future[Done] = {
    val promise = Promise[RecordMetadata]
    try {
      val message = Json.stringify(event)
      producer.send(new ProducerRecord[Array[Byte], String](topic, message), callback(promise))
    } catch {
      case NonFatal(e) =>
        promise.failure(e)
    }
    promise.future.fast.map { _ =>
      Done
    }
  }

  def close() =
    producer.close()

  private def callback(promise: Promise[RecordMetadata]) =
    new Callback {
      override def onCompletion(metadata: RecordMetadata, exception: Exception) =
        if (exception != null) {
          promise.failure(exception)
        } else {
          promise.success(metadata)
        }

    }
}
