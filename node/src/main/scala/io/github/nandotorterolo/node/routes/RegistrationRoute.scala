package io.github.nandotorterolo.node.routes

import cats.data.EitherT
import cats.effect.Async
import cats.effect.Sync
import cats.implicits._
import io.circe.syntax.EncoderOps
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.models.AccountSigned
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.ConflictMessage
import io.github.nandotorterolo.models.ModelThrowable.InvalidRequestParam
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.models.ModelThrowable.SignatureValidation
import io.github.nandotorterolo.node.interfaces.MemPoolService
import io.github.nandotorterolo.node.interfaces.StorageService
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl
import org.http4s.HttpRoutes
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import scodec.bits.ByteVector

object RegistrationRoute {

  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  def route[F[_]: Async](
      cripto: Cripto[F],
      storage: StorageService[F],
      mempool: MemPoolService[F]
  ): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {

      case req @ POST -> Root / "account" / "register" =>
        val response = for {
          accountSigned <-
            EitherT(
              req.body.compile
                .to(ByteVector)
                .map(bv => AccountSigned.codec.decode(bv.bits))
                .map(_.toEither)
            ).leftMap(_ => InvalidRequestParam: ModelThrowable)

          account = accountSigned.value.message

          publicKey <- EitherT(cripto.publicKey(account.address.publicKey.toArray.toVector))

          _ <- EitherT(accountSigned.value.validate[F](publicKey)(cripto))
            .flatMap(b => EitherT.cond[F](b, (), SignatureValidation: ModelThrowable))

          _ <- EitherT(storage.saveAccount(accountSigned.value))

          // a new account was created, now let provide a gift
          transactionSigned <- EitherT(storage.crateGiftTransaction(accountSigned.value))

          _ <- EitherT(Async[F].attempt(mempool.addTransaction(transactionSigned)))
            .leftMap(_ => Message("Error adding the tx in the mempool queue"): ModelThrowable)

        } yield accountSigned.value
        response.value
          .flatMap {
            case Right(accountSigned)      => Ok(accountSigned.asJson)
            case Left(InvalidRequestParam) => BadRequest(show"$InvalidRequestParam")
            case Left(SignatureValidation) => BadRequest(show"$SignatureValidation")
            case Left(ConflictMessage(s))  => Conflict(s"Service error: $s")
            case Left(Message(s))          => InternalServerError(s"Service error: $s")
            case Left(_)                   => BadRequest("other issues")
          }
          .recoverWith {
            case ex: Exception => InternalServerError(s"${ex.getMessage}")
          }

    }
  }

}
