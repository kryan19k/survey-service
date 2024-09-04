package com.mysurvey.metagraph.shared_data.calculated_state

import cats.effect.{Async, Ref}
import cats.syntax.all._
import com.mysurvey.metagraph.shared_data.types._
import com.mysurvey.metagraph.shared_data.calculated_state.postgres.PostgresService
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.security.hash.Hash
import org.tessellation.security.HashSelect

case class CalculatedState(ordinal: SnapshotOrdinal, state: SurveyCalculatedState)

object CalculatedState {
  implicit val encoder: Encoder[CalculatedState] = deriveEncoder
  implicit val decoder: Decoder[CalculatedState] = deriveDecoder
}

trait CalculatedStateService[F[_]] {
  def get: F[CalculatedState]
  def set(ordinal: SnapshotOrdinal, state: SurveyCalculatedState): F[Boolean]
  def update(f: CalculatedState => CalculatedState): F[CalculatedState]
  def hash(state: SurveyCalculatedState)(implicit hs: HashSelect): F[Hash]
  def applySnapshot(snapshot: SurveySnapshot): F[Unit]
}

object CalculatedStateService {
  def make[F[_]: Async](postgresService: PostgresService[F]): F[CalculatedStateService[F]] =
    Ref.of[F, CalculatedState](CalculatedState(SnapshotOrdinal.MinValue, SurveyCalculatedState(Map.empty, Map.empty, Map.empty, 0, 0, BigInt(0)))).map { ref =>
      new CalculatedStateService[F] {
        def get: F[CalculatedState] = ref.get.flatMap { state =>
          postgresService.getLatestState.flatMap {
            case Some(dbState) if dbState.ordinal > state.ordinal => ref.set(dbState) *> ref.get
            case _ => state.pure[F]
          }
        }

        def set(ordinal: SnapshotOrdinal, state: SurveyCalculatedState): F[Boolean] =
          ref.modify { current =>
            if (ordinal > current.ordinal) {
              val newState = CalculatedState(ordinal, state)
              (newState, true)
            } else (current, false)
          }.flatTap {
            case true => postgresService.saveState(CalculatedState(ordinal, state))
            case false => Async[F].unit
          }

        def update(f: CalculatedState => CalculatedState): F[CalculatedState] =
          ref.updateAndGet(f).flatTap(newState => postgresService.saveState(newState))

        def hash(state: SurveyCalculatedState)(implicit hs: HashSelect): F[Hash] =
          Async[F].delay {
            import java.security.MessageDigest
            val serializedState = io.circe.Encoder[SurveyCalculatedState].apply(state).noSpaces
            val bytes = serializedState.getBytes("UTF-8")
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(bytes)
            Hash.fromBytes(hashBytes)
          }

        def applySnapshot(snapshot: SurveySnapshot): F[Unit] =
          ref.update { _ =>
            CalculatedState(
              ordinal = snapshot.ordinal,
              state = SurveyCalculatedState(
                surveys = snapshot.surveys,
                responses = snapshot.responses,
                rewards = snapshot.rewards,
                totalSurveys = snapshot.surveys.size,
                totalResponses = snapshot.responses.values.map(_.size).sum,
                totalRewardsDistributed = snapshot.rewards.values.sum
              )
            )
          } *> postgresService.saveState(CalculatedState(
            snapshot.ordinal,
            SurveyCalculatedState(
              surveys = snapshot.surveys,
              responses = snapshot.responses,
              rewards = snapshot.rewards,
              totalSurveys = snapshot.surveys.size,
              totalResponses = snapshot.responses.values.map(_.size).sum,
              totalRewardsDistributed = snapshot.rewards.values.sum
            )
          ))
      }
    }
}