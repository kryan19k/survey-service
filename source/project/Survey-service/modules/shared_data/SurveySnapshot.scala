package com.my.survey.shared_data.types

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.snapshot.{Snapshot, SnapshotInfo}

case class SurveySnapshot(
  ordinal: SnapshotOrdinal,
  surveys: Map[String, Survey],
  responses: Map[String, List[SurveyResponse]],
  rewards: Map[String, BigInt]
) extends Snapshot

object SurveySnapshot {
  implicit val encoder: Encoder[SurveySnapshot] = deriveEncoder
  implicit val decoder: Decoder[SurveySnapshot] = deriveDecoder

  implicit val snapshotInfoEncoder: Encoder[SnapshotInfo[SurveySnapshot]] = deriveEncoder
  implicit val snapshotInfoDecoder: Decoder[SnapshotInfo[SurveySnapshot]] = deriveDecoder
}