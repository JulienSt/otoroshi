package otoroshi.storage.stores

import otoroshi.env.Env
import otoroshi.models._
import play.api.libs.json.Format
import otoroshi.storage.{RedisLike, RedisLikeStore}

class KvGlobalJwtVerifierDataStore(redisCli: RedisLike, _env: Env)
    extends GlobalJwtVerifierDataStore
    with RedisLikeStore[GlobalJwtVerifier] {

  override def redisLike(using env: Env): RedisLike     = redisCli
  override def fmt: Format[GlobalJwtVerifier]              = GlobalJwtVerifier._fmt
  override def key(id: String): String                     = s"${_env.storageRoot}:jwt:verifiers:$id"
  override def extractId(value: GlobalJwtVerifier): String = value.id
}
