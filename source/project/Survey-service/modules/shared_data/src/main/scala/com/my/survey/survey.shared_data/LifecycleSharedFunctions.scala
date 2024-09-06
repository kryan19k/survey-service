package com.my.survey.shared_data.survey.shared_data

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all._
import com.my.survey.shared_data.survey.shared_data.Utils.{getAllAddressesFromProofs, getFirstAddressFromProofs}
import com.my.survey.shared_data.survey.shared_data.combiners.Combiners._
import com.my.survey.shared_data.survey.shared_data.types._
import com.my.survey.shared_data.survey.shared_data.validations.Validations._
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.currency.dataApplication.{DataState, L0NodeContext}
import org.tessellation.security.SecurityProvider
import org.tessellation.security.signature.Signed
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object LifecycleSharedFunctions {
  private def logger[F[_] : Async]: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("SurveyApi")

  def validateUpdate[F[_] : Async](
    update: SurveyUpdate
  ): F[DataApplicationValidationErrorOr[Unit]] = Async[F].delay {
    update match {
      case createSurvey: CreateSurvey =>
        createSurveyValidations(createSurvey, None)
      case submitResponse: SubmitResponse =>
        submitResponseValidations(submitResponse, None)
    }
  }

  def validateData[F[_] : Async](
    state  : DataState[SurveyState, SurveyCalculatedState],
    updates: NonEmptyList[Signed[SurveyUpdate]]
  )(implicit context: L0NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] = {
    implicit val sp: SecurityProvider[F] = context.securityProvider
    updates.traverse { signedUpdate =>
      getAllAddressesFromProofs(signedUpdate.proofs)
        .flatMap { addresses =>
          Async[F].delay {
            signedUpdate.value match {
              case createSurvey: CreateSurvey =>
                createSurveyValidationsWithSignature(createSurvey, addresses, state)
              case submitResponse: SubmitResponse =>
                submitResponseValidationsWithSignature(submitResponse, addresses, state)
            }
          }
        }
    }.map(_.reduce)
  }

  def combine[F[_] : Async](
    state  : DataState[SurveyState, SurveyCalculatedState],
    updates: List[Signed[SurveyUpdate]]
  )(implicit context: L0NodeContext[F]): F[DataState[SurveyState, SurveyCalculatedState]] = {
    val newStateF = DataState(SurveyState(List.empty), state.calculated).pure[F]

    if (updates.isEmpty) {
      logger.info("Snapshot without any updates, updating the state to empty updates") >> newStateF
    } else {
      implicit val sp: SecurityProvider[F] = context.securityProvider
      newStateF.flatMap(newState => {
        updates.foldLeftM(newState) { (acc, signedUpdate) => {
          signedUpdate.value match {
            case createSurvey: CreateSurvey =>
              getFirstAddressFromProofs(signedUpdate.proofs)
                .map(combineCreateSurvey(createSurvey, acc, _))
            case submitResponse: SubmitResponse =>
              Async[F].delay(combineSubmitResponse(submitResponse, acc))
          }
        }
        }
      })
    }
  }
}