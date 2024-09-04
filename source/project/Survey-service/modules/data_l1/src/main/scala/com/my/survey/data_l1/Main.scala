package com.my.currency.data_l1

import cats.effect.{IO, Resource}
import cats.syntax.option._
import com.mysurvey.metagraph.shared_data.app.ApplicationConfigOps
import com.mysurvey.metagraph.shared_data.calculated_state.CalculatedStateService
import com.mysurvey.metagraph.shared_data.calculated_state.postgres.PostgresService
import com.mysurvey.metagraph.shared_data.types.codecs.JsonBinaryCodec
import com.mysurvey.metagraph.l1.SurveyL1Service
import com.mysurvey.metagraph.l1.custom_routes.CustomRoutes
import com.mysurvey.metagraph.shared_data.types.SurveySnapshot
import org.tessellation.BuildInfo
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.l1.CurrencyL1App
import org.tessellation.ext.cats.effect.ResourceIO
import org.tessellation.json.JsonSerializer
import org.tessellation.schema.cluster.ClusterId
import org.tessellation.schema.semver.{MetagraphVersion, TessellationVersion}
import org.tessellation.node.shared.domain.snapshot.SnapshotOps

import java.util.UUID

object Main extends CurrencyL1App(
  "survey-data_l1",
  "Survey data L1 node",
  ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
  tessellationVersion = TessellationVersion.unsafeFrom(BuildInfo.version),
  metagraphVersion = MetagraphVersion.unsafeFrom(BuildInfo.version)
) {

  override def dataApplication: Option[Resource[IO, DataApplicationL1Service[IO] with SnapshotOps[IO, SurveySnapshot]]] = {
    (for {
      implicit0(json2bin: JsonSerializer[IO]) <- JsonBinaryCodec.forSync[IO].asResource
      config <- ApplicationConfigOps.readDefault[IO].asResource
      dbCredentials = config.postgresDatabase
      postgresService <- PostgresService.make[IO](dbCredentials.url, dbCredentials.user, dbCredentials.password)
      calculatedStateService <- CalculatedStateService.make[IO](postgresService).asResource
      surveyL1Service <- SurveyL1Service.make[IO](calculatedStateService)
      customRoutes = CustomRoutes[IO](calculatedStateService)
      enhancedL1Service = surveyL1Service.withCustomRoutes(customRoutes)
    } yield enhancedL1Service).some
  }

  // Add any additional L1-specific configurations or overrides here
}