package com.my.survey.l1

import cats.effect.{IO, Resource}
import cats.syntax.option._
import com.my.survey.shared_data.app.ApplicationConfigOps
import com.my.survey.shared_data.calculated_state.CalculatedStateService
import com.my.survey.shared_data.calculated_state.postgres.PostgresService
import com.my.survey.shared_data.types.codecs.JsonBinaryCodec
import com.my.survey.shared_data.types.SurveySnapshot
import org.tessellation.BuildInfo
import org.tessellation.currency.l1.CurrencyL1App
import org.tessellation.ext.cats.effect.ResourceIO
import org.tessellation.json.JsonSerializer
import org.tessellation.schema.cluster.ClusterId
import org.tessellation.schema.semver.{MetagraphVersion, TessellationVersion}
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.UUID

object Main extends CurrencyL1App(
  "survey-l1",
  "Survey L1 node",
  ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
  metagraphVersion = MetagraphVersion.unsafeFrom(BuildInfo.version),
  tessellationVersion = TessellationVersion.unsafeFrom(BuildInfo.version)
) {
  override def dataApplication: Option[Resource[IO, SurveyL1Service[IO]]] = {
    (for {
      implicit0(json2bin: JsonSerializer[IO]) <- JsonBinaryCodec.forSync[IO].asResource
      config <- ApplicationConfigOps.readDefault[IO].asResource
      dbCredentials = config.postgresDatabase
      postgresService <- PostgresService.make[IO](dbCredentials.url, dbCredentials.user, dbCredentials.password)
      calculatedStateService <- CalculatedStateService.make[IO](postgresService).asResource
      tokenService <- TokenService.make[IO]().asResource
      rateLimiter <- RateLimiter.make[IO]().asResource
      logger <- Slf4jLogger.create[IO].asResource
      surveyL1Service <- SurveyL1Service.make[IO](calculatedStateService, tokenService, rateLimiter, logger)
    } yield surveyL1Service).some
  }
}