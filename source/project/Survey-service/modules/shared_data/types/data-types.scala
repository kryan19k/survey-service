package com.my.survey.shared_data.types

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import org.tessellation.schema.address.Address
import org.tessellation.schema.SnapshotOrdinal

import cats.data.ValidatedNel
import org.tessellation.currency.dataApplication.DataApplicationValidationErrorOr

type SurveyValidationResult[A] = ValidatedNel[DataApplicationValidationError, A]
type SurveyDataApplicationValidationErrorOr[A] = DataApplicationValidationErrorOr[A]

case class SurveySnapshot(
  ordinal: SnapshotOrdinal,
  state: SurveyState
)

object SurveySnapshot {
  implicit val encoder: Encoder[SurveySnapshot] = deriveEncoder
  implicit val decoder: Decoder[SurveySnapshot] = deriveDecoder
}
import java.util.UUID

case class Survey(
  id: UUID,
  creator: Address,
  questions: List[String],
  tokenReward: BigInt,
  imageUri: String,
  createdAt: Long, // Add timestamp for sorting and analysis
  publicKey: String // Add public key for encryption
)

object Survey {
  implicit val encoder: Encoder[Survey] = deriveEncoder
  implicit val decoder: Decoder[Survey] = deriveDecoder
}

case class SurveyResponse(
  surveyId: UUID,
  respondent: Address,
  encryptedAnswers: String, // Changed from 'answers' to 'encryptedAnswers'
  earnedReward: BigInt, // Add earned reward for this response
  submittedAt: Long // Add timestamp for sorting and analysis
)

object SurveyResponse {
  implicit val encoder: Encoder[SurveyResponse] = deriveEncoder
  implicit val decoder: Decoder[SurveyResponse] = deriveDecoder
}

case class SurveyState(
  surveys: Map[UUID, Survey],
  responses: Map[UUID, List[SurveyResponse]],
  rewards: Map[Address, BigInt]
)

object SurveyState {
  implicit val encoder: Encoder[SurveyState] = deriveEncoder
  implicit val decoder: Decoder[SurveyState] = deriveDecoder
}

sealed trait SurveyUpdate
case class CreateSurvey(survey: Survey) extends SurveyUpdate
case class SubmitResponse(response: SurveyResponse) extends SurveyUpdate

object SurveyUpdate {
  implicit val encoder: Encoder[SurveyUpdate] = {
    case cs: CreateSurvey => Encoder[CreateSurvey].apply(cs)
    case sr: SubmitResponse => Encoder[SubmitResponse].apply(sr)
  }
  implicit val decoder: Decoder[SurveyUpdate] =
    List[Decoder[SurveyUpdate]](
      Decoder[CreateSurvey].widen,
      Decoder[SubmitResponse].widen
    ).reduceLeft(_ or _)

  implicit val createSurveyEncoder: Encoder[CreateSurvey] = deriveEncoder
  implicit val createSurveyDecoder: Decoder[CreateSurvey] = deriveDecoder
  implicit val submitResponseEncoder: Encoder[SubmitResponse] = deriveEncoder
  implicit val submitResponseDecoder: Decoder[SubmitResponse] = deriveDecoder
}

case class SurveyCalculatedState(
  surveys: Map[UUID, Survey],
  responses: Map[UUID, List[SurveyResponse]],
  rewards: Map[Address, BigInt],
  totalSurveys: Int,
  totalResponses: Int,
  totalRewardsDistributed: BigInt
)

object SurveyCalculatedState {
  implicit val encoder: Encoder[SurveyCalculatedState] = deriveEncoder
  implicit val decoder: Decoder[SurveyCalculatedState] = deriveDecoder
}



case class SurveySnapshot(
  ordinal: SnapshotOrdinal,
  state: SurveyState
)

object SurveySnapshot {
  implicit val encoder: Encoder[SurveySnapshot] = deriveEncoder
  implicit val decoder: Decoder[SurveySnapshot] = deriveDecoder
}

sealed trait DataApplicationValidationError
case class InvalidSurvey(reason: String) extends DataApplicationValidationError
case class InvalidResponse(reason: String) extends DataApplicationValidationError

object DataApplicationValidationError {
  implicit val encoder: Encoder[DataApplicationValidationError] = deriveEncoder
  implicit val decoder: Decoder[DataApplicationValidationError] = deriveDecoder
}