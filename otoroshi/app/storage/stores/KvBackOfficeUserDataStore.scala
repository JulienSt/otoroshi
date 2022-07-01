package otoroshi.storage.stores

import akka.http.scaladsl.util.FastFuture
import akka.util.ByteString
import otoroshi.env.Env
import otoroshi.models.{BackOfficeUser, BackOfficeUserDataStore}
import otoroshi.storage.{RedisLike, RedisLikeStore}
import play.api.libs.json.{Format, JsSuccess, JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

class KvBackOfficeUserDataStore(redisCli: RedisLike, _env: Env)
    extends BackOfficeUserDataStore
    with RedisLikeStore[BackOfficeUser] {

  override def redisLike(implicit env: Env): RedisLike  = redisCli
  override def fmt: Format[BackOfficeUser]              = BackOfficeUser.fmt
  override def key(id: String): String                  = s"${_env.storageRoot}:users:backoffice:${id}"
  override def extractId(value: BackOfficeUser): String = value.randomId

  override def blacklisted(email: String)(implicit ec: ExecutionContext, env: Env): Future[Boolean] =
    redisCli.sismember(s"${env.storageRoot}:users:blacklist:backoffice", email)

  override def hasAlreadyLoggedIn(email: String)(implicit ec: ExecutionContext, env: Env): Future[Boolean] =
    redisCli.sismember(s"${env.storageRoot}:users:alreadyloggedin", email)

  override def alreadyLoggedIn(email: String)(implicit ec: ExecutionContext, env: Env): Future[Long] =
    redisCli.sadd(s"${env.storageRoot}:users:alreadyloggedin", email)

  override def sessions()(implicit ec: ExecutionContext, env: Env): Future[Seq[JsValue]] =
    redisCli
      .keys(s"${env.storageRoot}:users:backoffice:*")
      .flatMap(keys =>
        if (keys.isEmpty) FastFuture.successful(Seq.empty[Option[ByteString]])
        else redisCli.mget(keys: _*)
      )
      .map { seq =>
        seq
          .filter(_.isDefined)
          .map(_.get)
          .map(v => Json.parse(v.utf8String))
      }

  override def tsessions()(implicit ec: ExecutionContext, env: Env): Future[Seq[BackOfficeUser]] = {
    sessions().map(ses => ses.map(BackOfficeUser.fmt.reads).collect { case JsSuccess(value, _) => value })
  }

  override def discardSession(id: String)(implicit ec: ExecutionContext, env: Env): Future[Long] =
    redisCli.del(s"${env.storageRoot}:users:backoffice:$id")

  def discardAllSessions()(implicit ec: ExecutionContext, env: Env): Future[Long] =
    redisCli.keys(s"${env.storageRoot}:users:backoffice:*").flatMap { keys =>
      redisCli.del(keys: _*)
    }
}
