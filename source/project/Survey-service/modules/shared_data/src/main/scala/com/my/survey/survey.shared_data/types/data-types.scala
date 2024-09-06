package com.my.survey.shared_data.survey.shared_data.types

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto._
import org.tessellation.schema.address.Address
import cats.data.ValidatedNel
import com.my.survey.shared_data.survey.shared_data.calculated_state.CalculatedState
import org.tessellation.currency.dataApplication.{DataCalculatedState, DataOnChainState, DataUpdate}

import java.util.UUID
import java.time.Instant

object SurveyTypes {
  type SurveyValidationResult[A] = ValidatedNel[DataApplicationValidationError, A]
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

object SurveyStatus {
  implicit val encoder: Encoder[SurveyStatus] = Encoder.encodeString.contramap {
    case Active => "Active"
    case Completed => "Completed"
    case Cancelled => "Cancelled"
  }

  implicit val decoder: Decoder[SurveyStatus] = Decoder.decodeString.emap {
    case "Active" => Right(Active)
    case "Completed" => Right(Completed)
    case "Cancelled" => Right(Cancelled)
    case other => Left(s"Invalid SurveyStatus: $other")
  }
}

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
) extends DataOnChainState

object SurveyState {
  implicit val encoder: Encoder[SurveyState] = deriveEncoder[SurveyState]
  implicit val decoder: Decoder[SurveyState] = deriveDecoder[SurveyState]
}

sealed trait SurveyUpdate extends DataUpdate
case class CreateSurvey(survey: Survey) extends SurveyUpdate
case class SubmitResponse(response: SurveyResponse) extends SurveyUpdate

object SurveyUpdate {
  implicit val encoder: Encoder[SurveyUpdate] = Encoder.instance {
    case cs: CreateSurvey => Json.obj(
      ("type", Json.fromString("CreateSurvey")),
      ("survey", Encoder[Survey].apply(cs.survey))
    )
    case sr: SubmitResponse => Json.obj(
      ("type", Json.fromString("SubmitResponse")),
      ("response", Encoder[SurveyResponse].apply(sr.response))
    )
  }

  implicit val decoder: Decoder[SurveyUpdate] = Decoder.instance { c =>
    c.downField("type").as[String].flatMap {
      case "CreateSurvey" => c.downField("survey").as[Survey].map(CreateSurvey)
      case "SubmitResponse" => c.downField("response").as[SurveyResponse].map(SubmitResponse)
      case other => Left(DecodingFailure(s"Unknown SurveyUpdate type: $other", c.history))
    }
  }
}

case class SurveyCalculatedState(
  surveys: Map[UUID, Survey],
  responses: Map[UUID, List[SurveyResponse]],
  rewards: Map[Address, BigInt],
  totalSurveys: Int,
  totalResponses: Int,
  totalRewardsDistributed: BigInt
) extends DataCalculatedState

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