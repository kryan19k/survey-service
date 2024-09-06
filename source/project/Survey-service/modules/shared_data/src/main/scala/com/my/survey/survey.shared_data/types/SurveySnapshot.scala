package com.my.survey.shared_data.survey.shared_data.types

import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.generic.semiauto._
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.snapshot.Snapshot
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.NonNegative
import org.tessellation.currency.dataApplication.DataOnChainState

case class SurveySnapshot(
  ordinal: SnapshotOrdinal,
  surveys: Map[String, Survey],
  responses: Map[String, List[SurveyResponse]],
  rewards: Map[String, BigInt]
)

object SurveySnapshot {
  implicit val encoder: Encoder[SurveySnapshot] = deriveEncoder[SurveySnapshot]
  implicit val decoder: Decoder[SurveySnapshot] = deriveDecoder[SurveySnapshot]

  // Encoder and decoder for SurveySnapshot with ordinal
  implicit val surveySnapshotWithOrdinalEncoder: Encoder[(SnapshotOrdinal, SurveySnapshot)] = 
    new Encoder[(SnapshotOrdinal, SurveySnapshot)] {
      final def apply(a: (SnapshotOrdinal, SurveySnapshot)): Json = Json.obj(
        ("ordinal", Json.fromLong(a._1.value.value)),
        ("snapshot", encoder(a._2))
      )
    }

  implicit val surveySnapshotWithOrdinalDecoder: Decoder[(SnapshotOrdinal, SurveySnapshot)] = 
    new Decoder[(SnapshotOrdinal, SurveySnapshot)] {
      final def apply(c: HCursor): Decoder.Result[(SnapshotOrdinal, SurveySnapshot)] = 
        for {
          ordinalLong <- c.downField("ordinal").as[Long]
          ordinal <- Refined.unsafeApply[Long, NonNegative](ordinalLong) match {
            case refinedLong: Refined[Long, NonNegative] => Right(SnapshotOrdinal(refinedLong))
            case _ => Left(io.circe.DecodingFailure("Invalid SnapshotOrdinal", c.history))
          }
          snapshot <- c.downField("snapshot").as[SurveySnapshot]
        } yield (ordinal, snapshot)
    }
}