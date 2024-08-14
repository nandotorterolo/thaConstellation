package io.github.nandotorterolo.node.validator

import cats.data.EitherT
import cats.effect.Async
import cats.implicits._
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.models.Transaction
import io.github.nandotorterolo.node.interfaces.StorageService
import io.github.nandotorterolo.node.interfaces.TransactionNonceValidator

class TransactionNonceValidatorImpl[F[_]: Async](storageService: StorageService[F]) extends TransactionNonceValidator[F] {

  override def validate(transaction: Transaction): F[Either[ModelThrowable, Unit]] = {
    (for {

      sourceAccount <- EitherT(
        storageService
          .getAccount(transaction.source)
          .map(_.toRight[ModelThrowable](Message("Source Account not found!")))
      )
      res = sourceAccount.latestUsedNonce < transaction.nonce
      cond <- EitherT.cond[F](res, (), Message("Repeated transaction"): ModelThrowable)

    } yield cond).value

  }

}
object TransactionNonceValidatorImpl {
  def build[F[_]: Async](storageService: StorageService[F]): TransactionNonceValidator[F] =
    new TransactionNonceValidatorImpl(storageService)
}
