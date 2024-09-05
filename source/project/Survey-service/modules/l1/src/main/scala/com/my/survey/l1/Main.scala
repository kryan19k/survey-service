package com.my.survey.l1

import cats.effect.{IO, Resource}
import cats.syntax.option._
import com.my.survey.shared_data.app.ApplicationConfigOps
import com.my.survey.shared_data.calculated_state.CalculatedStateService
import com.my.survey.shared_data.calculated_state.postgres.PostgresService
import com.my.survey.shared_data.types.codecs.JsonBinaryCodec
import com.my.survey.shared_data.types.SurveySnapshot
import org.tessellation.BuildInfo
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.l1.CurrencyL1App
import org.tessellation.ext.cats.effect.ResourceIO
import org.tessellation.json.JsonSerializer
import org.tessellation.schema.cluster.ClusterId
import org.tessellation.schema.semver.{MetagraphVersion, TessellationVersion}
import org.tessellation.node.shared.domain.snapshot.SnapshotOps
import com.my.survey.shared_data._


import java.util.UUID

object Main extends CurrencyL1App(
  "survey-data_l1",
  "Survey data L1 node",
  ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
  metagraphVersion = MetagraphVersion.unsafeFrom(BuildInfo.version),
  tessellationVersion = TessellationVersion.unsafeFrom(BuildInfo.version)
) {
  // This method sets up the data application
  override def dataApplication: Option[Resource[IO, DataApplicationL1Service[IO, SurveyUpdate, SurveyState, SurveyCalculatedState] with SnapshotOps[IO, SurveySnapshot]]] = {
    (for {
      // Set up JSON serialization 
      implicit0(json2bin: JsonSerializer[IO]) <- JsonBinaryCodec.forSync[IO].asResource
      config <- ApplicationConfigOps.readDefault[IO].asResource
      
      // Set up database connection
      dbCredentials = config.postgresDatabase
      postgresService <- PostgresService.make[IO](dbCredentials.url, dbCredentials.user, dbCredentials.password)
      
      // Initialize the state service 
      calculatedStateService <- CalculatedStateService.make[IO](postgresService).asResource
      
      // Create the main survey service 
      surveyL1Service <- SurveyL1Service.make[IO](calculatedStateService)
      
      // Set up custom routes
      customRoutes = CustomRoutes[IO](calculatedStateService)
      enhancedL1Service = surveyL1Service.withCustomRoutes(customRoutes)
    } yield enhancedL1Service).some
  }

  // Add any additional L1-specific configurations or overrides here
}