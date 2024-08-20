package io.github.nandotorterolo.node.routes

import cats.data.EitherT
import cats.effect.Async
import cats.implicits._
import io.circe.syntax.EncoderOps
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.models.AddressIdSigned
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.EntityNotFound
import io.github.nandotorterolo.models.ModelThrowable.InvalidRequestParam
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.models.ModelThrowable.SignatureValidation
import io.github.nandotorterolo.node.interfaces._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl
import org.http4s.HttpRoutes
import scodec.bits.ByteVector

object TransactionsByAccountRoute {

  def route[F[_]: Async](
      cripto: Cripto[F],
      storage: StorageService[F]
  ): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {

      case req @ POST -> Root / "account" / "transactions" =>
        val response = for {

          addressIdSignedRequest <-
            EitherT(
              req.body.compile
                .to(ByteVector)
                .map(_.bits)
                .map(AddressIdSigned.codec.decode)
                .map(_.toEither)
            ).leftMap(_ => InvalidRequestParam: ModelThrowable)

          accountAddressId = addressIdSignedRequest.value.message
          account <- EitherT(
            storage
              .getAccount(accountAddressId)
              .map(_.toRight[ModelThrowable](Message("Source Account not found!")))
          )

          publicKey <- EitherT(cripto.publicKey(account.address.publicKey.toArray.toVector))

          _ <- EitherT(addressIdSignedRequest.value.validate[F](publicKey)(cripto))
            .flatMap(b => EitherT.cond[F](b, (), SignatureValidation: ModelThrowable))

          // No validation are required, an account is able to fetch transaction were the account was the souce or destination
          transactions <- EitherT(storage.getTransactionByAccount(addressIdSignedRequest.value.message))

        } yield transactions

        response.value
          .flatMap {
            case Right(t)                  => Ok(t.asJson)
            case Left(InvalidRequestParam) => BadRequest(show"$InvalidRequestParam")
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
