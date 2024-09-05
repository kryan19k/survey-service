package com.my.survey.currency_l1

import cats.effect.{Async, Ref}
import cats.syntax.all._
import org.tessellation.schema.address.Address
import org.tessellation.security.error.TessellationError
import org.tessellation.currency.l0.ApiClient
import org.typelevel.log4cats.Logger

trait TokenService[F[_]] {
  def distributeReward(recipient: Address, amount: BigInt): F[Either[TessellationError, Unit]]
  def deductFee(payer: Address, amount: BigInt): F[Either[TessellationError, Unit]]
  def getBalance(address: Address): F[Either[TessellationError, BigInt]]
}

object TokenService {
  def make[F[_]: Async: Logger](
    apiClient: ApiClient[F],
    minTransferAmount: BigInt = 1,
    maxTransferAmount: BigInt = BigInt("1000000000000000000") // 1 quintillion, adjust as needed
  ): F[TokenService[F]] = {
    Ref.of[F, Map[Address, BigInt]](Map.empty).map { balancesRef =>
      new TokenService[F] {
        def distributeReward(recipient: Address, amount: BigInt): F[Either[TessellationError, Unit]] =
          for {
            _ <- Logger[F].info(s"Attempting to distribute reward of $amount to $recipient")
            result <- if (amount < minTransferAmount || amount > maxTransferAmount) {
              Async[F].pure(Left(TessellationError.Custom(s"Invalid reward amount: $amount")))
            } else {
              apiClient.transferDAG(recipient, amount)
            }
            _ <- result match {
              case Right(_) => Logger[F].info(s"Successfully distributed reward of $amount to $recipient")
              case Left(error) => Logger[F].error(s"Failed to distribute reward to $recipient: $error")
            }
          } yield result

        def deductFee(payer: Address, amount: BigInt): F[Either[TessellationError, Unit]] =
          for {
            _ <- Logger[F].info(s"Attempting to deduct fee of $amount from $payer")
            result <- (for {
              balance <- EitherT(getBalance(payer))
              _ <- EitherT.cond[F](balance >= amount, (), TessellationError.BalanceNotEnough)
              burnAddress = Address.DAGAddressGenerator.generate
              transferResult <- EitherT(apiClient.transferDAG(burnAddress, amount))
            } yield transferResult).value
            _ <- result match {
              case Right(_) => Logger[F].info(s"Successfully deducted fee of $amount from $payer")
              case Left(error) => Logger[F].error(s"Failed to deduct fee from $payer: $error")
            }
          } yield result

        def getBalance(address: Address): F[Either[TessellationError, BigInt]] =
          for {
            _ <- Logger[F].info(s"Fetching balance for $address")
            result <- apiClient.getDAGBalance(address)
            _ <- result match {
              case Right(balance) => Logger[F].info(s"Balance for $address: $balance")
              case Left(error) => Logger[F].error(s"Failed to fetch balance for $address: $error")
            }
          } yield result
      }
    }
  }
}