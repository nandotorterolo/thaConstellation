package io.github.nandotorterolo.node.routes

import cats.data.EitherT
import cats.effect.Async
import cats.implicits._
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.models.AddressIdSigned
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.EntityNotFound
import io.github.nandotorterolo.models.ModelThrowable.InvalidRequestParam
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.models.ModelThrowable.SignatureValidation
import io.github.nandotorterolo.node.interfaces.StorageService
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl
import org.http4s.HttpRoutes
import scodec.bits.ByteVector

object BalanceRoute {

  def route[F[_]: Async](cripto: Cripto[F], storage: StorageService[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {

      case req @ POST -> Root / "account" / "balance" =>
        val response = for {
          addressIdSignedRequest <- EitherT(
            req.body.compile
              .to(ByteVector)
              .map(bv => AddressIdSigned.codec.decode(bv.bits))
              .map(_.toEither)
          ).leftMap { _ => InvalidRequestParam: ModelThrowable }

          account <- EitherT.fromOptionF(
            storage.getAccount(addressIdSignedRequest.value.message),
            EntityNotFound: ModelThrowable
          )

          publicKey <- EitherT(cripto.publicKey(account.address.publicKey.toArray.toVector))

          _ <- EitherT(addressIdSignedRequest.value.validate[F](publicKey)(cripto))
            .flatMap(b =>
              EitherT
                .cond[F](b, account, SignatureValidation: ModelThrowable)
            )

        } yield account
        response.value
          .flatMap {
            case Right(account)            => Ok(account)
            case Left(InvalidRequestParam) => BadRequest(show"$InvalidRequestParam")
            case Left(SignatureValidation) => BadRequest(show"$SignatureValidation")
            case Left(EntityNotFound)      => NotFound(show"$EntityNotFound")
            case Left(Message(s))          => InternalServerError(s)
            case Left(o)                   => InternalServerError(show"$o")
          }
          .recoverWith {
            case th: Throwable => InternalServerError(th.getMessage)
          }
    }
  }

}
