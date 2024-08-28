package io.github.nandotorterolo.node.routes

import scala.util.Try

import cats.data.EitherT
import cats.effect.Async
import cats.implicits._
import io.circe.syntax.EncoderOps
import io.github.nandotorterolo.models.Account
import io.github.nandotorterolo.models.BlockId
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.EntityNotFound
import io.github.nandotorterolo.models.ModelThrowable.InvalidRequestParam
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.node.interfaces.StorageService
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl
import org.http4s.AuthedRoutes
import scodec.bits.ByteVector

object GetBlockByIdAuthedRoute {

  def route[F[_]: Async](storage: StorageService[F]): AuthedRoutes[Account, F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    AuthedRoutes.of[Account, F] {

      case GET -> Root / "block" / blockIdStr as _ =>
        val response = for {
          blockId <- EitherT
            .fromEither[F](Try(BlockId(ByteVector.fromValidBase58(blockIdStr))).toEither)
            .leftMap { _ => InvalidRequestParam: ModelThrowable }

          block <- EitherT(storage.getBlock(blockId))

        } yield block
        response.value
          .flatMap {
            case Right(block)              => Ok(block.asJson)
            case Left(InvalidRequestParam) => BadRequest(show"$InvalidRequestParam")
            case Left(EntityNotFound)      => NotFound("Block not found")
            case Left(Message(s))          => InternalServerError(s)
            case Left(_)                   => InternalServerError()
          }
          .recoverWith {
            case _: Exception => InternalServerError()
          }
    }
  }

}
