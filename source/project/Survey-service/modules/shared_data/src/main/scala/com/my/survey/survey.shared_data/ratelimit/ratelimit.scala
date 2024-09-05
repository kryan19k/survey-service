package com.my.survey.shared_data.survey.shared_data.ratelimit

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
    for {
      rateLimitsRef <- Ref.of[F, Map[Address, RateLimit]](Map.empty)
      clock = Clock[F]  // Explicitly use Clock[F]
    } yield new RateLimiter[F] {
      def checkLimit(address: Address): F[Boolean] =
        for {
          now <- clock.realTime.map(_.toMillis)  // Use the clock instance
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