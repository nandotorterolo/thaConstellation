package io.github.nandotorterolo.node.validator

import cats.data.EitherT
import cats.effect.Async
import cats.implicits._
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.models.TransactionSigned
import io.github.nandotorterolo.node.interfaces.StorageService
import io.github.nandotorterolo.node.interfaces.TransactionSignedValidator

class TransactionSignedValidatorImpl[F[_]: Async](
    cripto: Cripto[F],
    storageService: StorageService[F]
) extends TransactionSignedValidator[F] {

  override def validate(transactionSigned: TransactionSigned): F[Either[ModelThrowable, Unit]] = {
    (for {

      sourceAccount <- EitherT(
        storageService
          .getAccount(transactionSigned.message.source)
          .map(_.toRight[ModelThrowable](Message("Source Account not found!")))
      )

      _ <- EitherT(
        storageService
          .getAccount(transactionSigned.message.destination)
          .map(_.toRight[ModelThrowable](Message("Destination Account not found!")))
      )

      publicKey <- EitherT(
        cripto
          .publicKey(sourceAccount.address.publicKey.toArray.toVector)
      )

      cond <- EitherT(transactionSigned.validate[F](publicKey)(cripto))
        .flatMap(b => EitherT.cond[F](b, (), Message("SignatureValidation"): ModelThrowable))

    } yield cond).value

  }

}
object TransactionSignedValidatorImpl {
  def build[F[_]: Async](
      cripto: Cripto[F],
      storageService: StorageService[F]
  ): TransactionSignedValidator[F] =
    new TransactionSignedValidatorImpl(cripto, storageService)
}
