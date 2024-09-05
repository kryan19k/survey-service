package com.my.survey.l1.custom_routes

import cats.effect.Async
import cats.syntax.all._
import com.my.survey.shared_data.calculated_state.CalculatedStateService
import com.my.survey.shared_data.types._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.CORS
import org.tessellation.routes.internal.{InternalUrlPrefix, PublicRoutes}
import org.tessellation.schema.address.Address
import org.tessellation.security.signature.SignedData

case class CustomRoutes[F[_]: Async](calculatedStateService: CalculatedStateService[F]) extends Http4sDsl[F] with PublicRoutes[F] {
  // Reuse the same route implementations as in L0 CustomRoutes
  // You can copy most of the code from your L0 CustomRoutes

  // Example of a new L1-specific route:
  private val l1SpecificRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "l1" / "info" =>
      Ok("This is an L1 specific route")
  }

  private val routes: HttpRoutes[F] = {
    // Combine the reused routes from L0 with L1-specific routes
    l1SpecificRoutes <+> // your other routes from L0
  }

  val public: HttpRoutes[F] =
    CORS
      .policy
      .withAllowCredentials(false)
      .httpRoutes(routes)

  override protected def prefixPath: InternalUrlPrefix = InternalUrlPrefix.unsafe("/")
}