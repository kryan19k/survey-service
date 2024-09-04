package com.my.survey.l1

import cats.data.{EitherT, NonEmptyList, ValidatedNel}
import cats.effect.Async
import cats.syntax.all._
import com.my.survey.shared_data.calculated_state.CalculatedStateService
import com.my.survey.shared_data.encryption.Encryption
import com.my.survey.shared_data.types._
import com.my.survey.shared_data.token.TokenService
import com.my.survey.shared_data.rate_limiter.RateLimiter
import com.my.survey.shared_data.errors.Errors._
import com.my.survey.shared_data.validations.Validations
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
import cats.data.Validated
import org.typelevel.log4cats.Logger

import java.time.Instant
import java.util.UUID

class SurveyL1Service[F[_]: Async](
  calculatedStateService: CalculatedStateService[F],
  tokenService: TokenService[F],
  rateLimiter: RateLimiter[F],
  logger: Logger[F]
) extends DataApplicationL1Service[F, SurveyUpdate, SurveyState, SurveyCalculatedState]
  with SnapshotOps[F, SurveySnapshot] {

  override def validateData(
    state: DataState[SurveyState, SurveyCalculatedState],
    updates: NonEmptyList[Signed[SurveyUpdate]]
  )(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
    updates.traverse_ { update =>
      Validations.createSurveyValidationsWithSignature(update.value, context.proofAddresses, state)
        .orElse(Validations.submitResponseValidationsWithSignature(update.value, context.proofAddresses, state))
    }.pure[F]

  override def validateUpdate(update: SurveyUpdate)(implicit context: L1NodeContext[F]): F[ValidatedNel[DataApplicationValidationError, Unit]] =
    Async[F].delay {
      update match {
        case cs: CreateSurvey => Validations.createSurveyValidations(cs, None)
        case sr: SubmitResponse => Validations.submitResponseValidations(sr, None)
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
        rateLimiter.checkLimit(Address.fromString("dummy")).flatMap {
          case true => 
            (for {
              state <- EitherT.right(getCalculatedState)
              response <- EitherT.right(Ok(state._2.surveys.values.toList))
            } yield response).handleErrorWith(handleError)
          case false => 
            Async[F].pure(TooManyRequests("Rate limit exceeded"))
        }

      case GET -> Root / "surveys" / UUIDVar(surveyId) / "responses" =>
        (for {
          state <- EitherT.right(getCalculatedState)
          survey <- EitherT.fromOption[F](state._2.surveys.get(surveyId), NotFound(s"Survey not found: $surveyId"))
          responses <- EitherT.right(state._2.responses.get(surveyId).traverse { encryptedResponses =>
            encryptedResponses.traverse { encryptedResponse =>
              Encryption.decryptSurveyResponse(encryptedResponse.encryptedAnswers, survey.publicKey)
                .map(decryptedAnswers => encryptedResponse.copy(encryptedAnswers = decryptedAnswers))
            }
          })
          response <- EitherT.right(Ok(responses.getOrElse(List.empty)))
        } yield response).handleErrorWith(handleError)

      case GET -> Root / "rewards" / address =>
        (for {
          state <- EitherT.right(getCalculatedState)
          addressObj <- EitherT.fromOption[F](Address.from(address), BadRequest(s"Invalid address: $address"))
          reward = state._2.rewards.getOrElse(addressObj, BigInt(0))
          response <- EitherT.right(Ok(s"Reward balance for $address: $reward"))
        } yield response).handleErrorWith(handleError)

      case POST -> Root / "withdraw" / address =>
        (for {
          state <- EitherT.right(getCalculatedState)
          addressObj <- EitherT.fromOption[F](Address.from(address), BadRequest(s"Invalid address: $address"))
          reward = state._2.rewards.getOrElse(addressObj, BigInt(0))
          _ <- EitherT.cond[F](reward > 0, (), BadRequest("No rewards available for withdrawal"))
          withdrawalResult <- EitherT(withdrawReward(addressObj, reward))
          response <- EitherT.right(Ok(s"Withdrawn $reward tokens for $address"))
        } yield response).handleErrorWith(handleError)

      case GET -> Root / "statistics" =>
        (for {
          state <- EitherT.right(getCalculatedState)
          stats = state._2
          response <- EitherT.right(Ok(Map(
            "totalSurveys" -> stats.totalSurveys,
            "totalResponses" -> stats.totalResponses,
            "totalRewardsDistributed" -> stats.totalRewardsDistributed
          )))
        } yield response).handleErrorWith(handleError)
    }
  }

  private def withdrawReward(address: Address, amount: BigInt): F[Either[Throwable, Unit]] =
    tokenService.distributeReward(address, amount).attempt

  private def handleError(error: Throwable): F[Response[F]] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    error match {
      case e: DataApplicationValidationError => BadRequest(e.message)
      case _ => InternalServerError(s"An unexpected error occurred: ${error.getMessage}")
    }
  }

  private def handleUnrewardedSurveys(implicit context: L1NodeContext[F]): F[Unit] = {
    val currentTime = Instant.now()
    for {
      state <- getCalculatedState
      surveys = state._2.surveys
      unrewardedSurveys = surveys.filter { case (_, survey) =>
        survey.endTime.isBefore(currentTime) && state._2.responses.get(survey.id).forall(_.isEmpty)
      }
      _ <- unrewardedSurveys.toList.traverse_ { case (surveyId, survey) =>
        for {
          _ <- logger.info(s"Survey $surveyId has ended with no responses. Returning rewards to creator.")
          result <- tokenService.distributeReward(survey.creator, survey.tokenReward)
          _ <- result match {
            case Right(_) => logger.info(s"Successfully returned ${survey.tokenReward} tokens to ${survey.creator}")
            case Left(error) => logger.error(s"Failed to return tokens to ${survey.creator}: $error")
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
        Validated.validNel(())
      } else {
        Validated.invalidNel(SnapshotValidationError("Invalid snapshot ordinal"))
      }
    }

  override def applySnapshot(snapshot: SurveySnapshot): F[Unit] =
    calculatedStateService.applySnapshot(snapshot)
}

object SurveyL1Service {
  def make[F[_]: Async](
    calculatedStateService: CalculatedStateService[F],
    tokenService: TokenService[F],
    rateLimiter: RateLimiter[F],
    logger: Logger[F]
  ): F[SurveyL1Service[F]] =
    Async[F].delay(new SurveyL1Service[F](calculatedStateService, tokenService, rateLimiter, logger))
}