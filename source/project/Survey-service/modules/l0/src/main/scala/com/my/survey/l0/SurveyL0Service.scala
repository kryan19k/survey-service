package com.my.survey.l0

import cats.data.{ValidatedNel, EitherT}
import cats.effect.Async
import cats.syntax.all._
import com.my.survey.shared_data.survey.shared_data.calculated_state.CalculatedStateService
import com.my.survey.shared_data.survey.shared_data.types._
import com.my.survey.shared_data.survey.shared_data.rate_limiter.RateLimiter
import com.my.survey.shared_data.survey.shared_data.encryption.Encryption
import com.my.survey.shared_data.validations.{TypeValidators, Validations}
import io.circe.{Decoder, Encoder}
import org.http4s.HttpRoutes
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.dataApplication.dataApplication.{DataApplicationBlock, DataApplicationValidationErrorOr}
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.security.hash.Hash
import org.tessellation.node.shared.domain.snapshot.{SnapshotOps, SnapshotValidationError}
import org.tessellation.currency.l0.ApiClient
import org.tessellation.schema.address.Address
import org.tessellation.schema.transaction.TransactionAmount

import java.time.Instant

class SurveyL0Service[F[_]: Async](
  calculatedStateService: CalculatedStateService[F],
  apiClient: ApiClient[F],
  rateLimiter: RateLimiter[F]
) extends DataApplicationL0Service[F, SurveyUpdate, SurveyState, SurveyCalculatedState]
  with SnapshotOps[F, SurveySnapshot] {

  override def validateData(
    state: DataState[SurveyState, SurveyCalculatedState],
    block: DataApplicationBlock[SurveyUpdate]
  )(implicit context: L0NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
    block.updates.traverse_ { update =>
      validateUpdate(update.value).leftMap(_.headOption.getOrElse("Unknown error"))
    }

  override def validateUpdate(
    update: SurveyUpdate
  )(implicit context: L0NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
    update match {
      case CreateSurvey(survey) =>
        for {
          withinLimit <- rateLimiter.checkLimit(survey.creator)
          validationResult <- 
            if (!withinLimit) Async[F].pure("Rate limit exceeded".asLeft)
            else Async[F].delay {
              Validations.createSurveyValidations(survey, Some(context.state.state)).toEither.leftMap(_.toString)
            }
        } yield validationResult
      case SubmitResponse(response) =>
        for {
          withinLimit <- rateLimiter.checkLimit(response.respondent)
          validationResult <- 
            if (!withinLimit) Async[F].pure("Rate limit exceeded".asLeft)
            else Async[F].delay {
              Validations.submitResponseValidations(response, Some(context.state.state)).toEither.leftMap(_.toString)
            }
        } yield validationResult
    }

  override def combine(
    state: DataState[SurveyState, SurveyCalculatedState],
    block: DataApplicationBlock[SurveyUpdate]
  )(implicit context: L0NodeContext[F]): F[DataState[SurveyState, SurveyCalculatedState]] =
    block.updates.foldM(state) { (acc, update) =>
      updateState(acc.state, acc.calculatedState, update.value).map { case (newState, newCalculatedState) =>
        DataState(newState, newCalculatedState)
      }
    }

  private def updateState(
    state: SurveyState,
    calculatedState: SurveyCalculatedState,
    update: SurveyUpdate
  ): F[(SurveyState, SurveyCalculatedState)] = update match {
    case CreateSurvey(survey) =>
      for {
        deductResult <- deductFee(survey.creator, survey.tokenReward)
        result <- deductResult match {
          case Right(_) =>
            val newState = state.copy(
              surveys = state.surveys + (survey.id -> survey)
            )
            val newCalculatedState = calculatedState.copy(
              surveys = calculatedState.surveys + (survey.id -> survey),
              totalSurveys = calculatedState.totalSurveys + 1,
              totalRewardsDistributed = calculatedState.totalRewardsDistributed + survey.tokenReward
            )
            (newState, newCalculatedState).pure[F]
          case Left(error) =>
            Async[F].raiseError[(SurveyState, SurveyCalculatedState)](new Exception(s"Failed to deduct fee: $error"))
        }
      } yield result

    case SubmitResponse(response) =>
      for {
        survey <- state.surveys.get(response.surveyId).liftTo[F](new Exception(s"Survey ${response.surveyId} not found"))
        encryptedAnswers <- response.answers.traverse { answer =>
          Encryption.encryptSurveyResponse(answer.toString, survey.publicKey)
        }
        encryptedResponse = response.copy(encryptedAnswers = encryptedAnswers)
        newResponses = state.responses.get(response.surveyId) match {
          case Some(existingResponses) => existingResponses :+ encryptedResponse
          case None => List(encryptedResponse)
        }
        newState = state.copy(
          responses = state.responses + (response.surveyId -> newResponses)
        )
        newCalculatedState = calculatedState.copy(
          responses = calculatedState.responses + (response.surveyId -> newResponses),
          totalResponses = calculatedState.totalResponses + 1,
          totalRewardsDistributed = calculatedState.totalRewardsDistributed + response.earnedReward
        )
      } yield (newState, newCalculatedState)
  }

  private def deductFee(address: Address, amount: Long): F[Either[String, Unit]] =
    apiClient.transfer(address, apiClient.getBurnAddress, TransactionAmount(amount)).attempt.map {
      case Right(_) => Right(())
      case Left(error) => Left(s"Failed to deduct fee: ${error.getMessage}")
    }

  override def dataEncoder: Encoder[SurveyUpdate] = implicitly[Encoder[SurveyUpdate]]
  override def dataDecoder: Decoder[SurveyUpdate] = implicitly[Decoder[SurveyUpdate]]
  override def calculatedStateEncoder: Encoder[SurveyCalculatedState] = implicitly[Encoder[SurveyCalculatedState]]
  override def calculatedStateDecoder: Decoder[SurveyCalculatedState] = implicitly[Decoder[SurveyCalculatedState]]

  override def getCalculatedState(implicit context: L0NodeContext[F]): F[(SnapshotOrdinal, SurveyCalculatedState)] =
    calculatedStateService.get.map(calculatedState => (calculatedState.ordinal, calculatedState.state))

  override def setCalculatedState(
    ordinal: SnapshotOrdinal,
    state: SurveyCalculatedState
  )(implicit context: L0NodeContext[F]): F[Boolean] =
    calculatedStateService.set(ordinal, state)

  override def hashCalculatedState(
    state: SurveyCalculatedState
  )(implicit context: L0NodeContext[F]): F[Hash] =
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

  def withCustomRoutes(customRoutes: CustomRoutes[F]): SurveyL0Service[F] = {
    val combinedRoutes = customRoutes.public <+> HttpRoutes.empty[F]
    new SurveyL0Service[F](calculatedStateService, apiClient, rateLimiter) {
      override def routes(implicit context: L0NodeContext[F]): HttpRoutes[F] = combinedRoutes
    }
  }
}

object SurveyL0Service {
  def make[F[_]: Async](
    calculatedStateService: CalculatedStateService[F],
    apiClient: ApiClient[F],
    rateLimiter: RateLimiter[F]
  ): F[SurveyL0Service[F]] =
    Async[F].delay(new SurveyL0Service[F](calculatedStateService, apiClient, rateLimiter))
}