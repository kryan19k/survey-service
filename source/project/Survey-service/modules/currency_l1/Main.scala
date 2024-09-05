package com.my.survey.currency_l1

import cats.effect.{IO, Resource}
import cats.syntax.all._
import com.my.survey.shared_data.app.ApplicationConfigOps
import com.my.survey.shared_data.calculated_state.CalculatedStateService
import com.my.survey.shared_data.calculated_state.postgres.PostgresService
import com.my.survey.shared_data.types.codecs.JsonBinaryCodec
import com.my.survey.shared_data.types.SurveySnapshot
import org.tessellation.BuildInfo
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.l1.CurrencyL1App
import org.tessellation.ext.cats.effect.ResourceIO
import org.tessellation.json.JsonSerializer
import org.tessellation.schema.cluster.ClusterId
import org.tessellation.schema.semver.{MetagraphVersion, TessellationVersion}
import org.tessellation.security.SecurityProvider
import org.tessellation.node.shared.domain.snapshot.SnapshotOps
import org.tessellation.currency.l0.ApiClient

import java.util.UUID

object Main extends CurrencyL1App(
  "survey-currency-l1",
  "Survey Currency L1 node",
  ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
  metagraphVersion = MetagraphVersion.unsafeFrom(BuildInfo.version),
  tessellationVersion = TessellationVersion.unsafeFrom(BuildInfo.version)
) {
  override def dataApplication: Option[Resource[IO, DataApplicationL1Service[IO] with SnapshotOps[IO, SurveySnapshot]]] = {
    (for {
      implicit0(json2bin: JsonSerializer[IO]) <- JsonBinaryCodec.forSync[IO].asResource
      config <- ApplicationConfigOps.readDefault[IO].asResource
      
      dbCredentials = config.postgresDatabase
      postgresService <- PostgresService.make[IO](dbCredentials.url, dbCredentials.user, dbCredentials.password)
      
      calculatedStateService <- CalculatedStateService.make[IO](postgresService).asResource
      
      apiClient <- ApiClient.make[IO](/* parameters */).asResource
      tokenService <- TokenService.make[IO](apiClient).asResource
      
      currencyL1Service <- CurrencyL1Service.make[IO](calculatedStateService, tokenService)
    } yield currencyL1Service).some
  }
}

class CurrencyL1Service[F[_]](
  calculatedStateService: CalculatedStateService[F],
  tokenService: TokenService[F]
)(implicit F: Async[F]) extends DataApplicationL1Service[F] with SnapshotOps[F, SurveySnapshot] {

  override def validateData(state: DataState[_, _], block: DataApplicationBlock[_])(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] = {
    // Implement data validation logic
    F.pure(().validNel)
  }

  override def validateUpdate(update: Any)(implicit context: L1NodeContext[F]): F[ValidatedNel[DataApplicationValidationError, Unit]] = {
    // Implement update validation logic
    F.pure(().validNel)
  }

  override def combine(state: DataState[_, _], updates: List[Signed[_]])(implicit context: L1NodeContext[F]): F[DataState[_, _]] = {
    // Implement state combination logic
    F.pure(state)
  }

  override def getCalculatedState(implicit context: L1NodeContext[F]): F[(SnapshotOrdinal, _)] = {
    calculatedStateService.get.map(state => (state.ordinal, state.state))
  }

  override def setCalculatedState(ordinal: SnapshotOrdinal, state: _)(implicit context: L1NodeContext[F]): F[Boolean] = {
    calculatedStateService.set(ordinal, state)
  }

  override def hashCalculatedState(state: _)(implicit context: L1NodeContext[F]): F[Hash] = {
    calculatedStateService.hash(state)
  }

  // Implement other required methods from DataApplicationL1Service and SnapshotOps
  // ...

  def distributeReward(recipient: Address, amount: BigInt): F[Either[TessellationError, Unit]] = {
    tokenService.distributeReward(recipient, amount)
  }

  def deductFee(payer: Address, amount: BigInt): F[Either[TessellationError, Unit]] = {
    tokenService.deductFee(payer, amount)
  }

  def getBalance(address: Address): F[Either[TessellationError, BigInt]] = {
    tokenService.getBalance(address)
  }
}

object CurrencyL1Service {
  def make[F[_]: Async](
    calculatedStateService: CalculatedStateService[F],
    tokenService: TokenService[F]
  ): F[CurrencyL1Service[F]] = {
    Async[F].delay(new CurrencyL1Service[F](calculatedStateService, tokenService))
  }
}