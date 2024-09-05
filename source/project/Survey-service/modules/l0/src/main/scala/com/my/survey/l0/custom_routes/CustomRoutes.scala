package com.my.survey.l0.custom_routes

import cats.effect.Async
import cats.syntax.all._
import com.my.survey.shared_data.survey.shared_data.calculated_state.CalculatedStateService
import com.my.survey.shared_data.survey.shared_data.types.States._
import com.my.survey.shared_data.survey.shared_data.types.Updates._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.CORS
import org.tessellation.routes.internal.{InternalUrlPrefix, PublicRoutes}
import org.tessellation.schema.address.Address
import org.tessellation.security.signature.SignedData
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.UUID

case class CustomRoutes[F[_] : Async](calculatedStateService: CalculatedStateService[F]) extends Http4sDsl[F] with PublicRoutes[F] {
  implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

  @derive(decoder, encoder)
  case class CreateSurveyRequest(questions: List[String], owner: Address)

  @derive(decoder, encoder)
  case class SubmitResponseRequest(responses: Map[String, String], respondent: Address)

  @derive(decoder, encoder)
  case class SurveyDetails(id: UUID, questions: List[String], owner: Address, responseCount: Int)

  @derive(decoder, encoder)
  case class SurveyStatistics(totalSurveys: Int, totalResponses: Int)

  @derive(decoder, encoder)
  case class DetailedSurveyStatistics(
    totalSurveys: Int,
    totalResponses: Int,
    averageResponsesPerSurvey: Double,
    mostPopularSurvey: UUID
  )

  object PageQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("page")
  object PageSizeQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("pageSize")

  private val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "surveys" :? PageQueryParamMatcher(page) +& PageSizeQueryParamMatcher(pageSize) =>
      getAllSurveys(page.getOrElse(1), pageSize.getOrElse(10))
    case GET -> Root / "surveys" / UUIDVar(surveyId) => getSurveyDetails(surveyId)
    case GET -> Root / "surveys" / UUIDVar(surveyId) / "responses" => getSurveyResponses(surveyId)
    case req @ POST -> Root / "surveys" => createSurvey(req)
    case req @ PUT -> Root / "surveys" / UUIDVar(surveyId) => updateSurvey(surveyId, req)
    case req @ DELETE -> Root / "surveys" / UUIDVar(surveyId) => deleteSurvey(surveyId, req)
    case req @ POST -> Root / "surveys" / UUIDVar(surveyId) / "responses" => submitSurveyResponse(surveyId, req)
    case GET -> Root / "statistics" => getDetailedStatistics
  }

  private def getAllSurveys(page: Int, pageSize: Int): F[Response[F]] = {
    calculatedStateService.get
      .map(_.state.surveys.values.toList)
      .map(surveys => surveys.slice((page - 1) * pageSize, page * pageSize))
      .map(surveys => surveys.map(survey =>
        SurveyDetails(survey.id, survey.questions, survey.owner, survey.responses.size)
      ))
      .flatMap(surveys => Ok(surveys.asJson))
  }

  private def getSurveyDetails(surveyId: UUID): F[Response[F]] = {
    calculatedStateService.get
      .map(_.state.surveys.get(surveyId))
      .flatMap {
        case Some(survey) => 
          val details = SurveyDetails(survey.id, survey.questions, survey.owner, survey.responses.size)
          Ok(details.asJson)
        case None => NotFound(s"Survey with id $surveyId not found")
      }
  }

  private def getSurveyResponses(surveyId: UUID): F[Response[F]] = {
    calculatedStateService.get
      .map(_.state.surveys.get(surveyId))
      .flatMap {
        case Some(survey) => Ok(survey.responses.asJson)
        case None => NotFound(s"Survey with id $surveyId not found")
      }
  }

  private def createSurvey(req: Request[F]): F[Response[F]] = {
    req.decodeJson[SignedData[CreateSurveyRequest]].flatMap { signedRequest =>
      val request = signedRequest.value
      if (request.questions.isEmpty) {
        BadRequest("Survey must have at least one question")
      } else {
        val newSurvey = CreateSurvey(UUID.randomUUID(), request.questions, request.owner)
        calculatedStateService.update(state =>
          state.copy(state = state.state.copy(surveys = state.state.surveys + (newSurvey.id -> Survey(newSurvey.id, newSurvey.questions, newSurvey.owner, Map.empty))))
        ) *> Created(newSurvey.id.asJson)
      }
    }.handleErrorWith(err => BadRequest(s"Invalid request: ${err.getMessage}"))
  }

  private def updateSurvey(surveyId: UUID, req: Request[F]): F[Response[F]] = {
    req.decodeJson[SignedData[CreateSurveyRequest]].flatMap { signedRequest =>
      val request = signedRequest.value
      calculatedStateService.update { state =>
        state.state.surveys.get(surveyId) match {
          case Some(survey) if survey.owner == request.owner =>
            val updatedSurvey = survey.copy(questions = request.questions)
            state.copy(state = state.state.copy(surveys = state.state.surveys + (surveyId -> updatedSurvey)))
          case Some(_) => state // Owner mismatch, don't update
          case None => state // Survey not found, don't update
        }
      }.flatMap { updated =>
        if (updated.state.surveys.contains(surveyId)) Ok(s"Survey $surveyId updated")
        else NotFound(s"Survey $surveyId not found or you're not the owner")
      }
    }
  }

  private def deleteSurvey(surveyId: UUID, req: Request[F]): F[Response[F]] = {
    req.decodeJson[SignedData[Address]].flatMap { signedRequest =>
      val owner = signedRequest.value
      calculatedStateService.update { state =>
        state.state.surveys.get(surveyId) match {
          case Some(survey) if survey.owner == owner =>
            state.copy(state = state.state.copy(surveys = state.state.surveys - surveyId))
          case Some(_) => state // Owner mismatch, don't delete
          case None => state // Survey not found, don't delete
        }
      }.flatMap { updated =>
        if (!updated.state.surveys.contains(surveyId)) Ok(s"Survey $surveyId deleted")
        else NotFound(s"Survey $surveyId not found or you're not the owner")
      }
    }
  }

  private def submitSurveyResponse(surveyId: UUID, req: Request[F]): F[Response[F]] = {
    req.decodeJson[SignedData[SubmitResponseRequest]].flatMap { signedRequest =>
      val request = signedRequest.value
      calculatedStateService.update { state =>
        state.state.surveys.get(surveyId) match {
          case Some(survey) =>
            val updatedResponses = survey.responses + (request.respondent -> request.responses)
            val updatedSurvey = survey.copy(responses = updatedResponses)
            state.copy(state = state.state.copy(surveys = state.state.surveys + (surveyId -> updatedSurvey)))
          case None => state // Survey not found, don't update
        }
      }.flatMap { updated =>
        if (updated.state.surveys(surveyId).responses.contains(request.respondent)) 
          Ok(s"Response submitted for survey $surveyId")
        else 
          NotFound(s"Survey with id $surveyId not found")
      }
    }
  }

  private def getDetailedStatistics: F[Response[F]] = {
    calculatedStateService.get
      .map { state =>
        val surveys = state.state.surveys
        val totalSurveys = surveys.size
        val totalResponses = surveys.values.map(_.responses.size).sum
        val averageResponses = if (totalSurveys > 0) totalResponses.toDouble / totalSurveys else 0
        val mostPopularSurvey = if (surveys.nonEmpty) surveys.maxBy(_._2.responses.size)._1 else UUID.randomUUID()
        DetailedSurveyStatistics(
          totalSurveys = totalSurveys,
          totalResponses = totalResponses,
          averageResponsesPerSurvey = averageResponses,
          mostPopularSurvey = mostPopularSurvey
        )
      }
      .flatMap(stats => Ok(stats.asJson))
  }

  val public: HttpRoutes[F] =
    CORS
      .policy
      .withAllowCredentials(false)
      .httpRoutes(routes)

   override protected def prefixPath: InternalUrlPrefix = InternalUrlPrefix.unsafe("/")
}