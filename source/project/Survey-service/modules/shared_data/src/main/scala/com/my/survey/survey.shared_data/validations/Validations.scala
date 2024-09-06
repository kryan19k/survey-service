package com.my.survey.shared_data.survey.shared_data.validations

import cats.syntax.all._
import cats.syntax.option._
import com.my.survey.shared_data.survey.shared_data.errors.Errors._
import com.my.survey.shared_data.survey.shared_data.types.{CreateSurvey, SubmitResponse, SurveyCalculatedState, SurveyState}
import com.my.survey.shared_data.survey.shared_data.validations.TypeValidators._
import org.tessellation.currency.dataApplication.DataState
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.schema.address.Address

object Validations {
  def createSurveyValidations(
    update: CreateSurvey,
    maybeState: Option[DataState[SurveyState, SurveyCalculatedState]]
  ): DataApplicationValidationErrorOr[Unit] =
    maybeState match {
      case Some(state) =>
        validateIfSurveyIsUnique(update, state)
          .productR(validateStringMaxSize(update.survey.title, 128, "title"))
          .productR(validateStringMaxSize(update.survey.description, 1024, "description"))
          .productR(validateListMaxSize(update.survey.questions, 50, "questions"))
          .productR(validateTokenReward(update.survey.tokenReward))
          .productR(validateImageUri(update.survey.imageUri))
          .productR(validateTimeRange(update.survey.createdAt, update.survey.endTime))
          .productR(validatePublicKey(update.survey.publicKey))
      case None =>
        validateStringMaxSize(update.survey.title, 128, "title")
          .productR(validateStringMaxSize(update.survey.description, 1024, "description"))
          .productR(validateListMaxSize(update.survey.questions, 50, "questions"))
          .productR(validateTokenReward(update.survey.tokenReward))
          .productR(validateImageUri(update.survey.imageUri))
          .productR(validateTimeRange(update.survey.createdAt, update.survey.endTime))
          .productR(validatePublicKey(update.survey.publicKey))
    }

  def submitResponseValidations(
    update: SubmitResponse,
    maybeState: Option[DataState[SurveyState, SurveyCalculatedState]]
  ): DataApplicationValidationErrorOr[Unit] =
    maybeState match {
      case Some(state) =>
        validateIfSurveyExists(update.response.surveyId, state)
          .productR(validateIfResponseIsUnique(update, state))
          .productR(validateResponseFormat(update.response, state))
          .productR(validateEarnedReward(update.response.earnedReward, state))
          .productR(validateSubmissionTime(update.response.submittedAt, state))
      case None =>
        valid
    }

  def createSurveyValidationsWithSignature(
    update: CreateSurvey,
    addresses: List[Address],
    state: DataState[SurveyState, SurveyCalculatedState]
  ): DataApplicationValidationErrorOr[Unit] =
    createSurveyValidations(update, state.some)
      .productR(validateProvidedAddress(addresses, update.creator))

  def submitResponseValidationsWithSignature(
    update: SubmitResponse,
    addresses: List[Address],
    state: DataState[SurveyState, SurveyCalculatedState]
  ): DataApplicationValidationErrorOr[Unit] =
    submitResponseValidations(update, state.some)
      .productR(validateProvidedAddress(addresses, update.response.respondent))
}