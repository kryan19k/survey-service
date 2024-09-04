package com.my.survey.l1

import cats.data.{EitherT, NonEmptyList, ValidatedNel}
import cats.effect.Async
import cats.syntax.all._
import com.my.survey.shared_data.calculated_state.CalculatedStateService
import com.my.survey.shared_data.types.States._
import com.my.survey.shared_data.types.Updates._
import com.my.survey.shared_data.types.{Survey, SurveySnapshot}
import com.my.survey.shared_data.token.TokenService
import io.circe.{Decoder, Encoder}
import org.http4s.{HttpRoutes, Response}
import org.http4s.dsl.Http4sDsl
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed
import org.tessellation.node.shared.domain.snapshot.{SnapshotOps, SnapshotValidationError}
import org.tessellation.schema.address.Address

import java.time.Instant
import java.util.UUID

class SurveyL1Service[F[_]: Async](
  calculatedStateService: CalculatedStateService[F],
  tokenService: TokenService[F]
) extends DataApplicationL1Service[F, SurveyUpdate, SurveyState, SurveyCalculatedState]
  with SnapshotOps[F, SurveySnapshot] {

  override def validateData(
    state: DataState[SurveyState, SurveyCalculatedState],
    updates: NonEmptyList[Signed[SurveyUpdate]]
  )(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
    Async[F].delay {
      updates.traverse_ { update =>
        validateUpdate(update.value).leftMap(_.headOption.getOrElse("Unknown error"))
      }
    }

  override def validateUpdate(
    update: SurveyUpdate
  )(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
    Async[F].delay {
      update match {
        case CreateSurvey(survey) =>
          if (survey.questions.nonEmpty && survey.tokenReward > 0) ().asRight 
          else "Survey must have at least one question and a positive reward".asLeft
        case SubmitResponse(response) =>
          if (response.encryptedAnswers.nonEmpty) ().asRight 
          else "Response must answer at least one question".asLeft
      }
    }

  override def combine(
    state: DataState[SurveyState, SurveyCalculatedState],
    updates: List[Signed[SurveyUpdate]]
  )(implicit context: L1NodeContext[F]): F[DataState[SurveyState, SurveyCalculatedState]] = {
    for {
      newState <- Async[F].delay {
        val (newSurveyState, newCalculatedState) = updates.foldLeft((state.state, state.calculatedState)) { 
          case ((accState, accCalcState), signedUpdate) =>
            updateState(accState, accCalcState, signedUpdate.value)
        }
        DataState(newSurveyState, newCalculatedState)
      }
      _ <- handleUnrewardedSurveys
    } yield newState
  }

  private def updateState(
    state: SurveyState,
    calculatedState: SurveyCalculatedState,
    update: SurveyUpdate
  ): (SurveyState, SurveyCalculatedState) = update match {
    case CreateSurvey(survey) =>
      val newState = state.copy(
        surveys = state.surveys + (survey.id -> survey)
      )
      val newCalculatedState = calculatedState.copy(
        surveys = calculatedState.surveys + (survey.id -> survey),
        totalSurveys = calculatedState.totalSurveys + 1,
        totalRewardsDistributed = calculatedState.totalRewardsDistributed + survey.tokenReward
      )
      (newState, newCalculatedState)

    case SubmitResponse(response) =>
      val newResponses = state.responses.get(response.surveyId) match {
        case Some(existingResponses) => existingResponses :+ response
        case None => List(response)
      }
      val newState = state.copy(
        responses = state.responses + (response.surveyId -> newResponses)
      )
      val newCalculatedState = calculatedState.copy(
        responses = calculatedState.responses + (response.surveyId -> newResponses),
        totalResponses = calculatedState.totalResponses + 1,
        totalRewardsDistributed = calculatedState.totalRewardsDistributed + response.earnedReward
      )
      (newState, newCalculatedState)
  }

  override def routes(implicit context: L1NodeContext[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._

    HttpRoutes.of[F] {
      case GET -> Root / "surveys" =>
        (for {
          state <- EitherT.right(getCalculatedState)
          response <- EitherT.right(Ok(state._2.surveys.values.toList))
        } yield response).handleErrorWith(handleError(_))

      case GET -> Root / "surveys" / UUIDVar(surveyId) =>
        (for {
          state <- EitherT.right(getCalculatedState)
          survey <- EitherT.fromOption[F](state._2.surveys.get(surveyId), NotFound(s"Survey not found: $surveyId"))
          response <- EitherT.right(Ok(survey))
        } yield response).handleErrorWith(handleError(_))

      case GET -> Root / "rewards" / address =>
        (for {
          state <- EitherT.right(getCalculatedState)
          addressObj <- EitherT.fromOption[F](Address.fromString(address), BadRequest(s"Invalid address: $address"))
          reward = state._2.rewards.getOrElse(addressObj, BigInt(0))
          response <- EitherT.right(Ok(s"Reward balance for $address: $reward"))
        } yield response).handleErrorWith(handleError(_))

      case POST -> Root / "withdraw" / address =>
        (for {
          state <- EitherT.right(getCalculatedState)
          addressObj <- EitherT.fromOption[F](Address.fromString(address), BadRequest(s"Invalid address: $address"))
          reward = state._2.rewards.getOrElse(addressObj, BigInt(0))
          _ <- EitherT.cond[F](reward > 0, (), BadRequest("No rewards available for withdrawal"))
          withdrawalResult <- EitherT(withdrawReward(addressObj, reward))
          response <- EitherT.right(Ok(s"Withdrawn $reward tokens for $address"))
        } yield response).handleErrorWith(handleError(_))

      case GET -> Root / "statistics" =>
        (for {
          state <- EitherT.right(getCalculatedState)
          stats = state._2
          response <- EitherT.right(Ok(Map(
            "totalSurveys" -> stats.totalSurveys,
            "totalResponses" -> stats.totalResponses,
            "totalRewardsDistributed" -> stats.totalRewardsDistributed
          )))
        } yield response).handleErrorWith(handleError(_))
    }
  }

  private def withdrawReward(address: Address, amount: BigInt): F[Either[Throwable, Unit]] =
    tokenService.distributeReward(address, amount).attempt

  private def handleError(error: Throwable): F[Response[F]] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    error match {
      case _: IllegalArgumentException => BadRequest(error.getMessage)
      case _ => InternalServerError(s"An unexpected error occurred: ${error.getMessage}")
    }
  }

  private def handleUnrewardedSurveys(implicit context: L1NodeContext[F]): F[Unit] = {
    val currentTime = Instant.now()
    for {
      state <- getCalculatedState
      surveys = state._2.surveys
      unrewardedSurveys = surveys.filter { case (_, survey) =>
        survey.endTime.isBefore(currentTime) && survey.responses.isEmpty
      }
      _ <- unrewardedSurveys.toList.traverse_ { case (surveyId, survey) =>
        for {
          _ <- Async[F].delay(println(s"Survey $surveyId has ended with no responses. Returning rewards to creator."))
          result <- tokenService.distributeReward(survey.creator, survey.tokenReward)
          _ <- result match {
            case Right(_) => Async[F].delay(println(s"Successfully returned ${survey.tokenReward} tokens to ${survey.creator}"))
            case Left(error) => Async[F].delay(println(s"Failed to return tokens to ${survey.creator}: $error"))
          }
        } yield ()
      }
    } yield ()
  }

  override def dataEncoder: Encoder[SurveyUpdate] = implicitly[Encoder[SurveyUpdate]]
  override def dataDecoder: Decoder[SurveyUpdate] = implicitly[Decoder[SurveyUpdate]]

  override def calculatedStateEncoder: Encoder[SurveyCalculatedState] = implicitly[Encoder[SurveyCalculatedState]]
  override def calculatedStateDecoder: Decoder[SurveyCalculatedState] = implicitly[Decoder[SurveyCalculatedState]]

  override def getCalculatedState(implicit context: L1NodeContext[F]): F[(SnapshotOrdinal, SurveyCalculatedState)] =
    calculatedStateService.get.map(calculatedState => (calculatedState.ordinal, calculatedState.state))

  override def setCalculatedState(
    ordinal: SnapshotOrdinal,
    state: SurveyCalculatedState
  )(implicit context: L1NodeContext[F]): F[Boolean] =
    calculatedStateService.set(ordinal, state)

  override def hashCalculatedState(
    state: SurveyCalculatedState
  )(implicit context: L1NodeContext[F]): F[Hash] =
    calculatedStateService.hash(state)

  override def takeSnapshot(lastSnapshotOrdinal: SnapshotOrdinal): F[SurveySnapshot] =
    calculatedStateService.get.map { calculatedState =>
      SurveySnapshot(
        ordinal = lastSnapshotOrdinal.next,
        surveys = calculatedState.state.surveys,
        responses = calculatedState.state.responses,
        rewards = calculatedState.state.rewards
      )
    }

  override def validateSnapshot(snapshot: SurveySnapshot): F[ValidatedNel[SnapshotValidationError, Unit]] =
    calculatedStateService.get.map { currentState =>
      if (snapshot.ordinal > currentState.ordinal) {
        ().validNel
      } else {
        SnapshotValidationError("Invalid snapshot ordinal").invalidNel
      }
    }

  override def applySnapshot(snapshot: SurveySnapshot): F[Unit] =
    calculatedStateService.applySnapshot(snapshot)
}

object SurveyL1Service {
  def make[F[_]: Async](
    calculatedStateService: CalculatedStateService[F],
    tokenService: TokenService[F]
  ): F[SurveyL1Service[F]] =
    Async[F].delay(new SurveyL1Service[F](calculatedStateService, tokenService))
}