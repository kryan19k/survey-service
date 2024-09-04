package com.my.survey.shared_data.validations

import com.my.survey.shared_data.Utils.isValidURL
import com.my.survey.shared_data.errors.Errors._
import com.my.survey.shared_data.serializers.Serializers
import com.my.survey.shared_data.types._
import org.tessellation.currency.dataApplication.DataState
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.schema.address.Address
import org.tessellation.security.hash.Hash

object TypeValidators {
  private def getSurveyById(
    surveyId: String,
    state: DataState[SurveyState, SurveyCalculatedState]
  ): Option[Survey] = {
    state.calculated.surveys.get(surveyId)
  }

  def validateIfSurveyIsUnique(
    update: CreateSurvey,
    state: DataState[SurveyState, SurveyCalculatedState]
  ): DataApplicationValidationErrorOr[Unit] = {
    val surveyId = Hash.fromBytes(Serializers.serializeUpdate(update)).toString
    DuplicatedSurvey.whenA(state.calculated.surveys.contains(surveyId))
  }

  def validateIfSurveyExists(
    surveyId: String,
    state: DataState[SurveyState, SurveyCalculatedState]
  ): DataApplicationValidationErrorOr[Unit] =
    SurveyNotExists.unlessA(state.calculated.surveys.contains(surveyId))

  def validateIfResponseIsUnique(
    update: SubmitResponse,
    state: DataState[SurveyState, SurveyCalculatedState]
  ): DataApplicationValidationErrorOr[Unit] =
    ResponseAlreadySubmitted.whenA(
      state.calculated.responses.get(update.response.surveyId).exists(_.exists(_.respondent == update.response.respondent))
    )

  def validateResponseFormat(
    response: SurveyResponse,
    state: DataState[SurveyState, SurveyCalculatedState]
  ): DataApplicationValidationErrorOr[Unit] =
    getSurveyById(response.surveyId, state)
      .map { survey =>
        InvalidResponseFormat.unlessA(
          survey.questions.size == response.answers.size &&
          response.answers.forall(answer => survey.questions.exists(_.id == answer.questionId))
        )
      }
      .getOrElse(SurveyNotExists.invalid)

  def validateProvidedAddress(
    proofAddresses: List[Address],
    address: Address
  ): DataApplicationValidationErrorOr[Unit] =
    InvalidAddress.unlessA(proofAddresses.contains(address))

  def validateStringMaxSize(
    value: String,
    maxSize: Long,
    fieldName: String
  ): DataApplicationValidationErrorOr[Unit] =
    InvalidFieldSize(fieldName, maxSize).whenA(value.length > maxSize)

  def validateListMaxSize(
    value: List[_],
    maxSize: Long,
    fieldName: String
  ): DataApplicationValidationErrorOr[Unit] =
    InvalidFieldSize(fieldName, maxSize).whenA(value.size > maxSize)
}