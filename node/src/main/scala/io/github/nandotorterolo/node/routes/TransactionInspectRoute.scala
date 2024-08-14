package io.github.nandotorterolo.node.routes

import cats.data.EitherT
import cats.effect.Async
import cats.implicits._
import io.circe.syntax.EncoderOps
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.EntityNotFound
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.models.ModelThrowable.SignatureValidation
import io.github.nandotorterolo.models.TransactionIdAddressIdSigned
import io.github.nandotorterolo.node.interfaces._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl
import org.http4s.HttpRoutes
import scodec.bits.ByteVector

object TransactionInspectRoute {

  def route[F[_]: Async](
      cripto: Cripto[F],
      storage: StorageService[F]
  ): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {

      case req @ POST -> Root / "transaction" / "inspect" =>
        val response = for {

          txIdAddressIdSigned <-
            EitherT(
              req.body.compile
                .to(ByteVector)
                .map(_.bits)
                .map(TransactionIdAddressIdSigned.codec.decode)
                .map(_.toEither)
            ).leftMap(_ => Message("InvalidRequestParam"))

          transaction <- EitherT(storage.getTransaction(txIdAddressIdSigned.value.message.transactionId))

          accountAddressId = txIdAddressIdSigned.value.message.addressId
          account <- EitherT(
            storage
              .getAccount(accountAddressId)
              .map(_.toRight[ModelThrowable](Message("Source Account not found!")))
          )

          publicKey <- EitherT(cripto.publicKey(account.address.publicKey.toArray.toVector))

          _ <- EitherT(txIdAddressIdSigned.value.validate[F](publicKey)(cripto))
            .flatMap(b => EitherT.cond[F](b, (), SignatureValidation: ModelThrowable))

          // only are allowed to inspect a transaction, if the requester is the source or destination
          cond = accountAddressId.value.compare(transaction.source.value) == 0 ||
            accountAddressId.value.compare(transaction.destination.value) == 0

          _ <- EitherT.cond[F](cond, (), SignatureValidation: ModelThrowable)

        } yield transaction

        response.value
          .flatMap {
            case Right(t)                  => Ok(show"${t.asJson.noSpaces}")
            case Left(SignatureValidation) => Forbidden(show"$SignatureValidation")
            case Left(EntityNotFound)      => NotFound(show"$EntityNotFound")
            case Left(Message(s))          => InternalServerError(s)
            case Left(th)                  => InternalServerError(th.getMessage)
          }
          .recoverWith {
            case th: Exception => InternalServerError(th.getMessage)
          }

    }
  }

}
