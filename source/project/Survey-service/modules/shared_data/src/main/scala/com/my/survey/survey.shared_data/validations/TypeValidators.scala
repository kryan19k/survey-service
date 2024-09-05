package com.my.survey.shared_data.survey.shared_data.validations

import com.my.survey.shared_data.survey.shared_data.errors.Errors._
import org.tessellation.currency.dataApplication.DataState
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.schema.address.Address
import com.my.survey.shared_data.survey.shared_data.types.{SurveyState, SurveyCalculatedState, CreateSurvey, SubmitResponse, SurveyResponse, Survey}
import java.util.UUID
import java.time.Instant
import java.net.{URL, MalformedURLException}

object TypeValidators {
  def getSurveyById(
    surveyId: UUID,
    state: DataState[SurveyState, SurveyCalculatedState]
  ): Option[Survey] = {
    state.calculated.surveys.get(surveyId)
  }

  def validateTokenReward(reward: BigInt): DataApplicationValidationErrorOr[Unit] =
    InvalidTokenReward.unlessA(reward > 0)

  def validateImageUri(uri: String): DataApplicationValidationErrorOr[Unit] =
    InvalidImageUri.unlessA(isValidURL(uri))

  def validateTimeRange(start: Instant, end: Instant): DataApplicationValidationErrorOr[Unit] =
    InvalidTimeRange.unlessA(end.isAfter(start))

  def validatePublicKey(key: String): DataApplicationValidationErrorOr[Unit] =
    InvalidPublicKey.whenA(key.isEmpty)

  def validateEarnedReward(
    earned: BigInt,
    state: DataState[SurveyState, SurveyCalculatedState]
  ): DataApplicationValidationErrorOr[Unit] =
    InvalidEarnedReward.unlessA(earned >= 0 && earned <= state.calculated.totalRewardsDistributed)

  def validateSubmissionTime(
  submitted: Long,
    state: DataState[SurveyState, SurveyCalculatedState]
  ): DataApplicationValidationErrorOr[Unit] =
    InvalidSubmissionTime.unlessA(
      state.calculated.surveys.values.exists(survey =>
        submitted >= survey.createdAt.toEpochMilli && submitted <= survey.endTime.toEpochMilli
      )
    )

  def validateIfSurveyIsUnique(
    update: CreateSurvey,
    state: DataState[SurveyState, SurveyCalculatedState]
  ): DataApplicationValidationErrorOr[Unit] = {
    DuplicatedSurvey.whenA(state.calculated.surveys.contains(update.survey.id))
  }

  def validateIfSurveyExists(
    surveyId: UUID,  // Change the type to UUID
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
          response.encryptedAnswers.nonEmpty &&
          Instant.ofEpochMilli(response.submittedAt).isAfter(survey.createdAt) &&
          Instant.ofEpochMilli(response.submittedAt).isBefore(survey.endTime)
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

  private def isValidURL(url: String): Boolean = {
    try {
      new URL(url)
      true
    } catch {
      case _: MalformedURLException => false
    }
  }
}