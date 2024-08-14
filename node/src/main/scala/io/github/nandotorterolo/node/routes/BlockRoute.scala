package io.github.nandotorterolo.node.routes

import cats.data.EitherT
import cats.effect.Async
import cats.implicits._
import io.circe.syntax.EncoderOps
import io.github.nandotorterolo.models.BlockId
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.node.interfaces.StorageService
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl
import org.http4s.HttpRoutes
import scodec.bits.ByteVector

object BlockRoute {

  def route[F[_]: Async](storage: StorageService[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {

      case req @ POST -> Root / "block" =>
        val response = for {
          blockIdRequest <- EitherT(
            req.body.compile
              .to(ByteVector)
              .map(bv => BlockId.codec.decode(bv.bits))
              .map(_.toEither)
          ).leftMap { _ => Message("InvalidRequestParam"): ModelThrowable }

          block <- EitherT(storage.getBlock(blockIdRequest.value))

        } yield block
        response.value
          .flatMap {
            case Right(block)     => Ok(show"${block.asJson.noSpaces}")
            case Left(Message(s)) => InternalServerError(s)
            case Left(_)          => InternalServerError()
          }
          .recoverWith {
            case _: Exception => InternalServerError()
          }
    }
  }

}
