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
import io.github.nandotorterolo.node.interfaces.TransactionsMultipleNonceValidator

class TransactionsMultipleNonceValidatorImpl[F[_]: Async](storageService: StorageService[F]) extends TransactionsMultipleNonceValidator[F] {
  override def validate(transactions: Vector[Transaction]): F[Either[ModelThrowable, Unit]] = {

    val amounts: Map[AddressId, Vector[Int]] = transactions
      .map(t => t.source -> t)
      .groupMap(_._1)(_._2.nonce)

    Foldable[Vector]
      .existsM(amounts.toVector) { addressId_nonces =>
        // if the account is not there, true will fail with correct behavior
        EitherT
          .fromOptionF(storageService.getAccount(addressId_nonces._1), true)
          .map { account =>
            val nonces      = addressId_nonces._2
            val range       = Range.inclusive(nonces.min, nonces.max)
            val nextValid   = nonces.min != account.latestUsedNonce + 1
            val lenghtValid = range.size != nonces.size
            val rangeValid  = !range.toSet.subsetOf(nonces.toSet)
            nextValid || lenghtValid || rangeValid
          }
          .value
          .map(_.merge)
      }
      .map {
        case true  => (Message("Repeated transaction"): ModelThrowable).asLeft[Unit]
        case false => ().asRight[ModelThrowable]
      }

  }
}

object TransactionsMultipleNonceValidatorImpl {
  def build[F[_]: Async](
      storageService: StorageService[F]
  ): TransactionsMultipleNonceValidator[F] =
    new TransactionsMultipleNonceValidatorImpl[F](storageService)
}
