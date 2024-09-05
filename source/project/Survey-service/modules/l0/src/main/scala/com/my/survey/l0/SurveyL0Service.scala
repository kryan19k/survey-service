package com.my.survey.l0

import cats.data.{NonEmptyList, ValidatedNel}
import cats.effect.Async
import cats.syntax.all._
import com.my.survey.shared_data.calculated_state.CalculatedStateService
import com.my.survey.shared_data.types._
import com.my.survey.currency_l1.TokenService
import com.mysurvey.metagraph.shared_data.ratelimit.RateLimiter
import com.my.survey.shared_data.encryption.Encryption
import com.my.survey.shared_data.types.{Survey, SurveyResponse}
import io.circe.{Decoder, Encoder}
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.dataApplication.dataApplication.{DataApplicationBlock, DataApplicationValidationErrorOr}
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed
import org.tessellation.node.shared.domain.snapshot.{SnapshotOps, SnapshotValidationError}
import com.my.survey.shared_data.validations.TypeValidators
import com.my.survey.shared_data.validations.Validations




// SurveyL0Service is the main service class for handling survey operations in the L0 layer
// It extends DataApplicationL0Service to integrate with the blockchain framework
// and SnapshotOps to handle state snapshots
class SurveyL0Service[F[_]: Async](
  calculatedStateService: CalculatedStateService[F],
  tokenService: TokenService[F],
  rateLimiter: RateLimiter[F]
) extends DataApplicationL0Service[F, SurveyUpdate, SurveyState, SurveyCalculatedState]
  with SnapshotOps[F, SurveySnapshot] {

  // Validates the data in a block before it's added to the blockchain
  override def validateData(
    state: DataState[SurveyState, SurveyCalculatedState],
    block: DataApplicationBlock
  )(implicit context: L0NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
    Async[F].delay {
      block.updates.traverse_ { update =>
        validateUpdate(update.signed.value).leftMap(_.headOption.getOrElse("Unknown error"))
      }
    }

  // Validates individual updates (CreateSurvey or SubmitResponse)
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
              TypeValidators.validateIfSurveyIsUnique(survey, context.state) *>
              TypeValidators.validateStringMaxSize(survey.title, 128, "title") *>
              TypeValidators.validateStringMaxSize(survey.description, 1024, "description") *>
              TypeValidators.validateListMaxSize(survey.questions, 50, "questions")
            }
        } yield validationResult
      case SubmitResponse(response) =>
        for {
          withinLimit <- rateLimiter.checkLimit(response.respondent)
          validationResult <- 
            if (!withinLimit) Async[F].pure("Rate limit exceeded".asLeft)
            else Async[F].delay {
              TypeValidators.validateIfSurveyExists(response.surveyId, context.state) *>
              TypeValidators.validateIfResponseIsUnique(response, context.state) *>
              TypeValidators.validateResponseFormat(response, context.state)
            }
        } yield validationResult
    }

  override def combine(
    state: DataState[SurveyState, SurveyCalculatedState],
    block: DataApplicationBlock
  )(implicit context: L0NodeContext[F]): F[DataState[SurveyState, SurveyCalculatedState]] =
    block.updates.map(_.signed.value).foldM(state) { (acc, update) =>
      updateState(acc.state, acc.calculatedState, update).map { case (newState, newCalculatedState) =>
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
        deductResult <- tokenService.deductFee(survey.creator, survey.tokenReward)
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
            Async[F].raiseError[(SurveyState, SurveyCalculatedState)](new Exception(s"Failed to deduct fee: ${error.toString}"))
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

  private def calculateReward(survey: Survey, response: SurveyResponse): BigInt = {
    val baseReward = survey.tokenReward / BigInt(survey.questions.size)
    val completionPercentage = response.encryptedAnswers.length.toDouble / survey.questions.size
    (baseReward * BigDecimal(completionPercentage)).toBigInt
  }

  override def dataEncoder: Encoder[SurveyUpdate] = implicitly[Encoder[SurveyUpdate]]
  override def dataDecoder: Decoder[SurveyUpdate] = implicitly[Decoder[SurveyUpdate]]

  override def calculatedStateEncoder: Encoder[SurveyCalculatedState] = implicitly[Encoder[SurveyCalculatedState]]
  override def calculatedStateDecoder: Decoder[SurveyCalculatedState] = implicitly[Decoder[SurveyCalculatedState]]

  // Retrieves the current calculated state
  override def getCalculatedState(implicit context: L0NodeContext[F]): F[(SnapshotOrdinal, SurveyCalculatedState)] =
    calculatedStateService.get.map(calculatedState => (calculatedState.ordinal, calculatedState.state))

  // Sets a new calculated state
  override def setCalculatedState(
    ordinal: SnapshotOrdinal,
    state: SurveyCalculatedState
  )(implicit context: L0NodeContext[F]): F[Boolean] =
    calculatedStateService.set(ordinal, state)

  // Computes a hash of the calculated state
  override def hashCalculatedState(
    state: SurveyCalculatedState
  )(implicit context: L0NodeContext[F]): F[Hash] =
    calculatedStateService.hash(state)

  // Creates a snapshot of the current state
  override def takeSnapshot(lastSnapshotOrdinal: SnapshotOrdinal): F[SurveySnapshot] =
    calculatedStateService.get.map { calculatedState =>
      SurveySnapshot(
        ordinal = lastSnapshotOrdinal.next,
        surveys = calculatedState.state.surveys,
        responses = calculatedState.state.responses,
        rewards = calculatedState.state.rewards
      )
    }

  // Validates a snapshot before applying it
  override def validateSnapshot(snapshot: SurveySnapshot): F[ValidatedNel[SnapshotValidationError, Unit]] =
    calculatedStateService.get.map { currentState =>
      if (snapshot.ordinal > currentState.ordinal) {
        ().validNel
      } else {
        SnapshotValidationError("Invalid snapshot ordinal").invalidNel
      }
    }

  // Applies a validated snapshot to update the current state
  override def applySnapshot(snapshot: SurveySnapshot): F[Unit] =
    calculatedStateService.applySnapshot(snapshot)

  def withCustomRoutes(customRoutes: CustomRoutes[F]): SurveyL0Service[F] = {
    val combinedRoutes = customRoutes.public <+> HttpRoutes.empty[F] // L0 doesn't have default routes
    new SurveyL0Service[F](calculatedStateService, tokenService, rateLimiter) {
      override def routes(implicit context: L0NodeContext[F]): HttpRoutes[F] = combinedRoutes
    }
  }

}

// Companion object for creating instances of SurveyL0Service
object SurveyL0Service {
  def make[F[_]: Async](
    calculatedStateService: CalculatedStateService[F],
    tokenService: TokenService[F],
    rateLimiter: RateLimiter[F]
  ): F[SurveyL0Service[F]] =
    Async[F].delay(new SurveyL0Service[F](calculatedStateService, tokenService, rateLimiter))

