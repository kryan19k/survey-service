package com.my.survey.shared_data.survey.shared_data.types

import io.circe.{Decoder, Encoder, HCursor, Json, DecodingFailure}
import io.circe.generic.semiauto._
import org.tessellation.schema.address.Address
import cats.data.ValidatedNel
import java.util.UUID
import java.time.Instant

object SurveyTypes {
  type SurveyValidationResult[A] = ValidatedNel[DataApplicationValidationError, A]
  type SurveyDataApplicationValidationErrorOr[A] = DataApplicationValidationErrorOr[A]
}

case class Survey(
  id: UUID,
  creator: Address,
  questions: List[String],
  tokenReward: BigInt,
  imageUri: String,
  createdAt: Instant,
  endTime: Instant,
  publicKey: String,
  status: SurveyStatus
)

sealed trait SurveyStatus
case object Active extends SurveyStatus
case object Completed extends SurveyStatus
case object Cancelled extends SurveyStatus

object Survey {
  implicit val encoder: Encoder[Survey] = deriveEncoder[Survey]
  implicit val decoder: Decoder[Survey] = deriveDecoder[Survey]
}

case class SurveyResponse(
  surveyId: UUID,
  respondent: Address,
  encryptedAnswers: String,
  earnedReward: BigInt,
  submittedAt: Long
)

object SurveyResponse {
  implicit val encoder: Encoder[SurveyResponse] = deriveEncoder[SurveyResponse]
  implicit val decoder: Decoder[SurveyResponse] = deriveDecoder[SurveyResponse]
}

case class SurveyState(
  surveys: Map[UUID, Survey],
  responses: Map[UUID, List[SurveyResponse]],
  rewards: Map[Address, BigInt]
)

object SurveyState {
  implicit val encoder: Encoder[SurveyState] = deriveEncoder[SurveyState]
  implicit val decoder: Decoder[SurveyState] = deriveDecoder[SurveyState]
}

sealed trait SurveyUpdate
case class CreateSurvey(survey: Survey) extends SurveyUpdate
case class SubmitResponse(response: SurveyResponse) extends SurveyUpdate

object SurveyUpdate {
  implicit val encoder: Encoder[SurveyUpdate] = new Encoder[SurveyUpdate] {
    final def apply(a: SurveyUpdate): Json = a match {
      case cs: CreateSurvey => Json.obj(
        ("type", Json.fromString("CreateSurvey")),
        ("survey", Encoder[Survey].apply(cs.survey))
      )
      case sr: SubmitResponse => Json.obj(
        ("type", Json.fromString("SubmitResponse")),
        ("response", Encoder[SurveyResponse].apply(sr.response))
      )
    }
  }

  implicit val decoder: Decoder[SurveyUpdate] = new Decoder[SurveyUpdate] {
    final def apply(c: HCursor): Decoder.Result[SurveyUpdate] = 
      c.downField("type").as[String].flatMap {
        case "CreateSurvey" => c.downField("survey").as[Survey].map(CreateSurvey)
        case "SubmitResponse" => c.downField("response").as[SurveyResponse].map(SubmitResponse)
        case _ => Left(DecodingFailure("Unknown SurveyUpdate type", c.history))
      }
  }

  implicit val createSurveyEncoder: Encoder[CreateSurvey] = deriveEncoder[CreateSurvey]
  implicit val createSurveyDecoder: Decoder[CreateSurvey] = deriveDecoder[CreateSurvey]
  implicit val submitResponseEncoder: Encoder[SubmitResponse] = deriveEncoder[SubmitResponse]
  implicit val submitResponseDecoder: Decoder[SubmitResponse] = deriveDecoder[SubmitResponse]
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
  implicit val encoder: Encoder[SurveyCalculatedState] = deriveEncoder[SurveyCalculatedState]
  implicit val decoder: Decoder[SurveyCalculatedState] = deriveDecoder[SurveyCalculatedState]
}

sealed trait DataApplicationValidationError
case class InvalidSurvey(reason: String) extends DataApplicationValidationError
case class InvalidResponse(reason: String) extends DataApplicationValidationError

object DataApplicationValidationError {
  implicit val encoder: Encoder[DataApplicationValidationError] = deriveEncoder[DataApplicationValidationError]
  implicit val decoder: Decoder[DataApplicationValidationError] = deriveDecoder[DataApplicationValidationError]
}