package otoroshi.security

import java.nio.charset.StandardCharsets
import java.util.{Base64, Date}
import com.auth0.jwt.algorithms.Algorithm
import otoroshi.env.Env
import otoroshi.models.AlgoSettings
import org.joda.time.DateTime
import otoroshi.utils.syntax.implicits.BetterJsValue
import play.api.Logger
import play.api.libs.json._
import java.util.{Base64 => JavaBase64}

case class OtoroshiClaim(
    iss: String,                          // issuer
    sub: String,                          // subject
    aud: String,                          // audience
    exp: Long,                            // date d'expiration
    iat: Long = DateTime.now().getMillis, // issued at
    jti: String,                          // unique id forever
    metadata: JsObject = Json.obj()       // private claim
) {
  def toJson: JsValue                                                 = OtoroshiClaim.format.writes(this)
  def serialize(jwtSettings: AlgoSettings)(using env: Env): String = OtoroshiClaim.serialize(this, jwtSettings)(using env)
  def withClaims(claims: JsValue): OtoroshiClaim                      =
    copy(metadata = metadata ++ claims.asOpt[JsObject].getOrElse(Json.obj()))
  def withClaims(claims: Option[JsValue]): OtoroshiClaim              =
    claims match {
      case Some(c) => withClaims(c)
      case None    => this
    }
  def withClaim(name: String, value: String): OtoroshiClaim           = copy(metadata = metadata ++ Json.obj(name -> value))
  def withRootClaim(name: String, value: String): OtoroshiClaim       =
    this // copy(metadata = metadata ++ Json.obj(name -> value))
  def withClaim(name: String, value: Option[String]): OtoroshiClaim           =
    value match {
      case Some(v) => copy(metadata = metadata ++ Json.obj(name -> v))
      case None    => this
    }
  def withJsObjectClaim(name: String, value: Option[JsObject]): OtoroshiClaim =
    value match {
      case Some(v) => copy(metadata = metadata ++ Json.obj(name -> v))
      case None    => this
    }
  def withJsArrayClaim(name: String, value: Option[JsArray]): OtoroshiClaim   =
    value match {
      case Some(v) => copy(metadata = metadata ++ Json.obj(name -> v))
      case None    => this
    }

  def payload(using env: Env): JsObject = Json.obj(
    "iss" -> env.Headers.OtoroshiIssuer, // TODO: maybe using iss is better ?
    "sub" -> sub,
    "aud" -> aud,
    "exp" -> new Date(exp).getTime / 1000,
    "iat" -> new Date(iat).getTime / 1000,
    "nbr" -> new Date(iat).getTime / 1000,
    "jti" -> jti
  ) ++ metadata
}

object OtoroshiClaim {

  val encoder                        = Base64.getUrlEncoder
  val decoder                        = Base64.getUrlDecoder
  val format: OFormat[OtoroshiClaim] = Json.format[OtoroshiClaim]

  lazy val logger: Logger = Logger("otoroshi-claim")

  def serialize(claim: OtoroshiClaim, jwtSettings: AlgoSettings)(using env: Env): String = {
    val algorithm = jwtSettings.asAlgorithm(otoroshi.models.OutputMode).get
    // Here we bypass JWT lib limitations ...
    val header    = Json.obj(
      "typ" -> "JWT",
      "alg" -> algorithm.getName
    )
    val payload   = claim.payload
    val signed    = sign(algorithm, header, payload)
    if (logger.isDebugEnabled) logger.debug(s"signed: $signed")
    signed
  }

  private def sign(algorithm: Algorithm, headerJson: JsObject, payloadJson: JsObject): String = {
    if (logger.isDebugEnabled) {
      logger.debug(s"signing following header: ${headerJson.prettify}")
      logger.debug(s"signing following payload: ${payloadJson.prettify}")
    }
    val header: String              = JavaBase64.getUrlEncoder.withoutPadding().encodeToString(Json.toBytes(headerJson))
    val payload: String             = JavaBase64.getUrlEncoder.withoutPadding().encodeToString(Json.toBytes(payloadJson))
    val signatureBytes: Array[Byte] =
      algorithm.sign(header.getBytes(StandardCharsets.UTF_8), payload.getBytes(StandardCharsets.UTF_8))

    val signature: String = JavaBase64.getUrlEncoder.withoutPadding().encodeToString(signatureBytes)
    String.format("%s.%s.%s", header, payload, signature)
  }
}
