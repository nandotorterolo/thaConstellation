package io.github.nandotorterolo.node.routes

import cats.data.EitherT
import cats.effect.Async
import cats.implicits._
import io.circe.syntax.EncoderOps
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.InvalidRequestParam
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.models.TransactionSigned
import io.github.nandotorterolo.node.interfaces._
import io.github.nandotorterolo.node.service.MemPoolServiceQueue
import io.github.nandotorterolo.node.validator.TransactionValidator
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl
import org.http4s.HttpRoutes
import scodec.bits.ByteVector

object TransactionBroadcastRoute {

  def route[F[_]: Async](
      tCommonValidator: TransactionValidator[F],
      tSignV: TransactionSignedValidator[F],
      tOverdraftV: TransactionOverdraftValidator[F],
      tNonceV: TransactionNonceValidator[F],
      mempool: MemPoolServiceQueue[F]
  ): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {

      case req @ POST -> Root / "transaction" / "broadcast" =>
        val response = for {
          txSigned <-
            EitherT(
              req.body.compile
                .to(ByteVector)
                .map(_.bits)
                .map(TransactionSigned.codec.decode)
                .map(_.toEither)
            ).leftMap(_ => InvalidRequestParam: ModelThrowable)

          tx = txSigned.value.message

          _ <- EitherT(tCommonValidator.validate(tx))
          _ <- EitherT(tOverdraftV.validate(tx))
          _ <- EitherT(tNonceV.validate(tx))
          _ <- EitherT(tSignV.validate(txSigned.value))

          _ <- EitherT(Async[F].attempt(mempool.addTransaction(txSigned.value)))
            .leftMap(_ => Message("Error adding the tx in the mempool queue"): ModelThrowable)

        } yield tx
        response.value.flatMap {
          case Left(InvalidRequestParam) => BadRequest(show"$InvalidRequestParam")
          case Left(error)               => BadRequest(show"$error")
          case Right(tx)                 => Ok(tx.asJson)
        }

    }
  }

}
