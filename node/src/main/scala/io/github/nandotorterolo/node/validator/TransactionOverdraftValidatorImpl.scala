package io.github.nandotorterolo.node.validator

import cats.data.EitherT
import cats.effect.Async
import cats.implicits._
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.models.Transaction
import io.github.nandotorterolo.node.interfaces.StorageService
import io.github.nandotorterolo.node.interfaces.TransactionOverdraftValidator

class TransactionOverdraftValidatorImpl[F[_]: Async](storageService: StorageService[F]) extends TransactionOverdraftValidator[F] {

  override def validate(transaction: Transaction): F[Either[ModelThrowable, Unit]] = {
    (for {

      sourceAccount <- EitherT(
        storageService
          .getAccount(transaction.source)
          .map(_.toRight[ModelThrowable](Message("Source Account not found!")))
      )
      res = sourceAccount.balance >= transaction.amount
      cond <- EitherT.cond[F](res, (), Message("Overdraft"): ModelThrowable)

    } yield cond).value

  }

}
object TransactionOverdraftValidatorImpl {
  def build[F[_]: Async](storageService: StorageService[F]): TransactionOverdraftValidator[F] =
    new TransactionOverdraftValidatorImpl(storageService)
}
