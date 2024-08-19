package io.github.nandotorterolo.node.service

import cats.data.EitherT
import cats.effect.kernel.Async
import cats.effect.std.Queue
import cats.implicits._
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.models.Block
import io.github.nandotorterolo.models.BlockId
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.models.TransactionId
import io.github.nandotorterolo.models.TransactionSigned
import io.github.nandotorterolo.node.interfaces._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

class MemPoolServiceQueue[F[_]: Async](
    queue: Queue[F, TransactionSigned],
    storageService: StorageService[F],
    multipleFilter: TransactionsMultipleFilter[F],
    serverCredentials: ServerCredentials[F],
    cripto: Cripto[F]
) extends MemPoolService[F] {

  implicit val logger: Logger[F] = Slf4jLogger.getLogger[F]

  /**
   * Add a transaction to the mempol
   * @param t
   * @return
   */
  override def addTransaction(t: TransactionSigned): F[Unit] = {
    queue.offer(t)
  }

  /**
   * Procces a list of trnsaction in the mempool
   * @param txs transactions
   *  @return
   */
  override def processListTxs(txs: Vector[TransactionSigned]): F[Unit] = {
    (for {
      validTxs <- EitherT(multipleFilter.filterInvalids(txs).attempt)
        .leftMap(_ => Message("Error on filter operation"): ModelThrowable)

      head <- EitherT(storageService.getBlockHead)
      block = Block(
        priorBlock = BlockId(head.hash.value),
        sequenceNumber = head.message.sequenceNumber + 1,
        transactions = validTxs.map(_.hash.value).map(TransactionId(_))
      )

      privateKey <- EitherT(serverCredentials.getPrivateKey)
      publicKey  <- EitherT(serverCredentials.getPublicKey)

      blockSigned <- EitherT(block.sign(privateKey)(cripto))
      _ <- EitherT(blockSigned.validate[F](publicKey)(cripto))
        .flatMap(b => EitherT.cond[F](b, (), Message("SignatureValidation"): ModelThrowable))

      _ <- EitherT(storageService.saveBlock(blockSigned, validTxs))
        .leftSemiflatTap(th => Logger[F].warn(s"It was not possible create a block ${th.getMessage}"))
        .semiflatTap(bs => Logger[F].info(show"A new block was created ${bs}"))
    } yield ()).value.void

  }

}

object MemPoolServiceQueue {

  def impl[F[_]: Async](
      queue: Queue[F, TransactionSigned],
      storageService: StorageService[F],
      multipleFilter: TransactionsMultipleFilter[F],
      serverCredentials: ServerCredentials[F],
      cripto: Cripto[F]
  ): MemPoolService[F] =
    new MemPoolServiceQueue(queue, storageService, multipleFilter, serverCredentials, cripto)
}
