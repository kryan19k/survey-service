package com.mysurvey.metagraph.shared_data.ratelimit

import cats.effect.{Async, Ref, Clock}
import cats.syntax.all._
import org.tessellation.schema.address.Address
import scala.concurrent.duration._

case class RateLimit(count: Int, lastReset: Long)

trait RateLimiter[F[_]] {
  def checkLimit(address: Address): F[Boolean]
}

object RateLimiter {
  private val MaxRequests = 10
  private val ResetInterval = 1.hour.toMillis

  def make[F[_]: Async: Clock]: F[RateLimiter[F]] = {
    Ref.of[F, Map[Address, RateLimit]](Map.empty).map { rateLimitsRef =>
      new RateLimiter[F] {
        def checkLimit(address: Address): F[Boolean] =
          for {
            now <- Clock[F].realTime.map(_.toMillis)
            result <- rateLimitsRef.modify { rateLimits =>
              val currentLimit = rateLimits.getOrElse(address, RateLimit(0, now))
              val updatedLimit = 
                if (now - currentLimit.lastReset > ResetInterval) RateLimit(1, now)
                else currentLimit.copy(count = currentLimit.count + 1)
              val newRateLimits = rateLimits + (address -> updatedLimit)
              (newRateLimits, updatedLimit.count <= MaxRequests)
            }
          } yield result
      }
    }
  }
}