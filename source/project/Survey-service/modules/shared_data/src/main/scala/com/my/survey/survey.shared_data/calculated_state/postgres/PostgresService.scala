package com.my.survey.shared_data.survey.shared_data.calculated_state.postgres

import cats.effect.Async
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import io.circe.parser._
import io.circe.syntax._
import com.my.survey.shared_data.survey.shared_data.calculated_state.CalculatedState

trait PostgresService[F[_]] {
  def getLatestState: F[Option[CalculatedState]]
  def saveState(state: CalculatedState): F[Unit]
}

object PostgresService {
  def make[F[_]: Async](url: String, user: String, password: String): F[PostgresService[F]] = {
    val xa = Transactor.fromDriverManager[F](
      "org.postgresql.Driver",
      url,
      user,
      password
    )

    Async[F].delay {
      new PostgresService[F] {
        def getLatestState: F[Option[CalculatedState]] = {
          sql"SELECT state FROM calculated_state ORDER BY ordinal DESC LIMIT 1"
            .query[String]
            .option
            .transact(xa)
            .map(_.flatMap(s => decode[CalculatedState](s).toOption))
        }

        def saveState(state: CalculatedState): F[Unit] = {
          sql"INSERT INTO calculated_state (ordinal, state) VALUES (${state.ordinal}, ${state.asJson.noSpaces})"
            .update
            .run
            .transact(xa)
            .void
        }
      }
    }
  }
}