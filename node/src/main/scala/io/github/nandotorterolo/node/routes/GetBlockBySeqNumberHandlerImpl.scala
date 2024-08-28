package io.github.nandotorterolo.node.routes

import cats.data.EitherT
import cats.effect.Async
import cats.implicits._
import io.github.nandotorterolo.models.ModelThrowable.EntityNotFound
import io.github.nandotorterolo.models.ModelThrowable.InvalidRequestParam
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.node.interfaces.StorageService
import io.github.nandotorterolo.server.autogenerated.block.BlockHandler
import io.github.nandotorterolo.server.autogenerated.block.BlockResource
import io.github.nandotorterolo.server.autogenerated.definitions.BlockResponse
import io.github.nandotorterolo.server.autogenerated.definitions.ErrorModel
import org.http4s.Status

class GetBlockBySeqNumberHandlerImpl[F[_]: Async](storage: StorageService[F]) extends BlockHandler[F] {

  override def getBlockBySequenceNumber(
      respond: BlockResource.GetBlockBySequenceNumberResponse.type
  )(sequenceNumber: Int): F[BlockResource.GetBlockBySequenceNumberResponse] =
    EitherT(storage.getBlockBySeqNumber(sequenceNumber)).value
      .map {
        case Right(block) =>
          respond.Ok(
            BlockResponse(
              blockId = block.hash.value.toBase58,
              priorBlock = block.message.priorBlock.value.toBase58,
              sequenceNumber = block.message.sequenceNumber,
              transactions = block.message.transactions.map(_.value.toBase58).mkString(",")
            )
          )
        case Left(InvalidRequestParam) => respond.BadRequest(ErrorModel(show"$InvalidRequestParam", Status.BadRequest.code))
        case Left(EntityNotFound)      => respond.NotFound(ErrorModel(show"$EntityNotFound", Status.NotFound.code))
        case Left(Message(s))          => respond.InternalServerError(ErrorModel(show"$s", Status.InternalServerError.code))
        case Left(o)                   => respond.InternalServerError(ErrorModel(show"${o.getMessage}", Status.InternalServerError.code))

      }
}
