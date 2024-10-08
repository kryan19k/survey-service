package com.my.survey.l0

import cats.effect.{IO, Resource}
import cats.syntax.option._
import com.my.survey.shared_data.survey.shared_data.app.ApplicationConfigOps
import com.my.survey.shared_data.survey.shared_data.calculated_state.CalculatedStateService
import com.my.survey.shared_data.survey.shared_data.calculated_state.postgres.PostgresService
import com.my.survey.shared_data.types.codecs.JsonBinaryCodec
import com.my.survey.l0.custom_routes.CustomRoutes
import com.my.survey.shared_data.survey.shared_data.types.SurveySnapshot
import com.my.survey.shared_data.survey.shared_data.rate_limiter.RateLimiter
import org.tessellation.BuildInfo
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.l0.{CurrencyL0App, ApiClient}
import org.tessellation.ext.cats.effect.ResourceIO
import org.tessellation.json.JsonSerializer
import org.tessellation.schema.cluster.ClusterId
import org.tessellation.schema.semver.{MetagraphVersion, TessellationVersion}
import org.tessellation.node.shared.domain.snapshot.SnapshotOps
import org.tessellation.security.SecurityProvider
import org.tessellation.currency.schema.currency.{CurrencyIncrementalSnapshot, CurrencySnapshotStateProof}
import org.tessellation.node.shared.domain.rewards.Rewards
import org.tessellation.node.shared.snapshot.currency.CurrencySnapshotEvent

import java.util.UUID

object Main extends CurrencyL0App(
  "survey-l0",
  "Survey L0 node",
  ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
  metagraphVersion = MetagraphVersion.unsafeFrom(BuildInfo.version),
  tessellationVersion = TessellationVersion.unsafeFrom(BuildInfo.version)
) {

  override def dataApplication: Option[Resource[IO, DataApplicationL0Service[IO] with SnapshotOps[IO, SurveySnapshot]]] = {
    (for {
      implicit0(json2bin: JsonSerializer[IO]) <- JsonBinaryCodec.forSync[IO].asResource
      config <- ApplicationConfigOps.readDefault[IO].asResource
      dbCredentials = config.postgresDatabase
      postgresService <- PostgresService.make[IO](dbCredentials.url, dbCredentials.user, dbCredentials.password)
      calculatedStateService <- CalculatedStateService.make[IO](postgresService).asResource
      apiClient <- ApiClient.make[IO](/* parameters */).asResource
      rateLimiter <- RateLimiter.make[IO].asResource
      surveyL0Service <- SurveyL0Service.make[IO](calculatedStateService, apiClient, rateLimiter)
      customRoutes = CustomRoutes[IO](calculatedStateService)
      enhancedL0Service = surveyL0Service.withCustomRoutes(customRoutes)
    } yield enhancedL0Service).some
  }

  override def rewards(implicit sp: SecurityProvider[IO]): Some[Rewards[IO, CurrencySnapshotStateProof, CurrencyIncrementalSnapshot, CurrencySnapshotEvent]] = 
    Some(SurveyRewards.make[IO])
}