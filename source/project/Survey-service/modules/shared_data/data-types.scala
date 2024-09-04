package com.my.survey.shared_data.types

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import org.tessellation.schema.address.Address

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
  implicit val encoder: Encoder[SurveyUpdate] = deriveEncoder
  implicit val decoder: Decoder[SurveyUpdate] = deriveDecoder
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