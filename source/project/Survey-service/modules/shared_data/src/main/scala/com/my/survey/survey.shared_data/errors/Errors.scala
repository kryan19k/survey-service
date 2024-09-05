package com.my.survey.shared_data.survey.shared_data.errors

import cats.data.ValidatedNec
import cats.syntax.validated._
import org.tessellation.currency.dataApplication._


object Errors {
  type DataApplicationValidationType = ValidatedNec[DataApplicationValidationError, Unit]

  val valid: DataApplicationValidationType = ().validNec

  implicit class DataApplicationValidationTypeOps[E <: DataApplicationValidationError](err: E) {
    def invalid: DataApplicationValidationType = err.invalidNec

    def unlessA(cond: Boolean): DataApplicationValidationType =
      if (cond) valid else invalid

    def whenA(cond: Boolean): DataApplicationValidationType =
      if (cond) invalid else valid
  }

  case object DuplicatedSurvey extends DataApplicationValidationError {
    val message = "Duplicated survey"
  }

  case object InvalidSurveyUri extends DataApplicationValidationError {
    val message = "Survey URI is invalid"
  }

  case object SurveyAlreadyExists extends DataApplicationValidationError {
    val message = "Survey already exists"
  }

  case object SurveyNotExists extends DataApplicationValidationError {
    val message = "Survey does not exist"
  }

  case object SurveyDoesNotBelongToProvidedAddress extends DataApplicationValidationError {
    val message = "Survey does not belong to provided address"
  }

  case object ResponseAlreadySubmitted extends DataApplicationValidationError {
    val message = "Response already submitted for this survey"
  }

  case object InvalidResponseFormat extends DataApplicationValidationError {
    val message = "Invalid response format"
  }

  case object InsufficientRewardBalance extends DataApplicationValidationError {
    val message = "Insufficient reward balance for survey creation"
  }

  case object SurveyExpired extends DataApplicationValidationError {
    val message = "Survey has expired"
  }

  case object CouldNotGetLatestCurrencySnapshot extends DataApplicationValidationError {
    val message = "Could not get latest currency snapshot!"
  }

  case object CouldNotGetLatestState extends DataApplicationValidationError {
    val message = "Could not get latest state!"
  }

  case object InvalidAddress extends DataApplicationValidationError {
    val message = "Provided address different than proof"
  }

  case class InvalidFieldSize(fieldName: String, maxSize: Long) extends DataApplicationValidationError {
    val message = s"Invalid field size: $fieldName, maxSize: $maxSize"
  }

  case object InvalidTokenReward extends DataApplicationValidationError {
    val message = "Invalid token reward"
  }

  case object InvalidImageUri extends DataApplicationValidationError {
    val message = "Invalid image URI"
  }

  case object InvalidTimeRange extends DataApplicationValidationError {
    val message = "Invalid time range"
  }

  case object InvalidPublicKey extends DataApplicationValidationError {
    val message = "Invalid public key"
  }

  case object InvalidEarnedReward extends DataApplicationValidationError {
    val message = "Invalid earned reward"
  }

  case object InvalidSubmissionTime extends DataApplicationValidationError {
    val message = "Invalid submission time"
  }
}