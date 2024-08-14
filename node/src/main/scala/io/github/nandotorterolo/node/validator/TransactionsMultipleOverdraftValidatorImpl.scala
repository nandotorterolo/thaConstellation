package io.github.nandotorterolo.node.validator

import cats.data.EitherT
import cats.effect.Async
import cats.implicits._
import cats.Foldable
import io.github.nandotorterolo.models.AddressId
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.models.Transaction
import io.github.nandotorterolo.node.interfaces.StorageService
import io.github.nandotorterolo.node.interfaces.TransactionsMultipleOverdraftValidator

class TransactionsMultipleOverdraftValidatorImpl[F[_]: Async](storageService: StorageService[F]) extends TransactionsMultipleOverdraftValidator[F] {
  override def validate(transactions: Vector[Transaction]): F[Either[ModelThrowable, Unit]] = {

    val amounts: Map[AddressId, Double] = transactions
      .map(t => t.source -> t)
      .groupMapReduce(_._1)(_._2.amount)(_ + _)

    Foldable[Vector]
      .existsM(amounts.toVector) { addressId_Sumbalance =>
        // if the account is not there, true will fail with correct behavior
        EitherT
          .fromOptionF(storageService.getAccount(addressId_Sumbalance._1), true)
          .map(_.balance < addressId_Sumbalance._2)
          .value
          .map(_.merge)
      }
      .map {
        case true  => (Message("Overdraft"): ModelThrowable).asLeft[Unit]
        case false => ().asRight[ModelThrowable]
      }

  }
}

object TransactionsMultipleOverdraftValidatorImpl {
  def build[F[_]: Async](
      storageService: StorageService[F]
  ): TransactionsMultipleOverdraftValidator[F] =
    new TransactionsMultipleOverdraftValidatorImpl[F](storageService)
}
