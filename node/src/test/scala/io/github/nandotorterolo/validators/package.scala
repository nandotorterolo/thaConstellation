package io.github.nandotorterolo

import cats.effect.IO
import cats.implicits._
import io.github.nandotorterolo.models._
import io.github.nandotorterolo.models.ModelThrowable.EntityNotFound
import io.github.nandotorterolo.node.interfaces.StorageService

package object validators {

  def storageMock(
      accounts: Map[AddressId, Account] = Map.empty,
      blocks: Map[BlockId, BlockSigned] = Map.empty,
      transactions: Map[TransactionId, TransactionSigned] = Map.empty,
  ): StorageService[IO] =
    new StorageService[IO] {
      override def getAccount(accountId: AddressId): IO[Option[Account]] =
        accounts.get(accountId).pure[IO]

      override def saveAccount(account: AccountSigned): IO[Either[ModelThrowable, AccountSigned]] =
        ???

      override def getServerAccount: IO[Either[ModelThrowable, Account]] = ???

      override def createServerAccount(): IO[Either[ModelThrowable, AccountSigned]] = ???

      override def saveBlock(block: BlockSigned, txs: Vector[TransactionSigned]): IO[Either[ModelThrowable, BlockSigned]] = ???

      override def getBlockHead: IO[Either[ModelThrowable, BlockSigned]] = ???

      override def getBlock(blockId: BlockId): IO[Either[ModelThrowable, BlockSigned]] =
        blocks.get(blockId).toRight(EntityNotFound: ModelThrowable).pure[IO]

      override def getBlockBySeqNumber(seqNumber: Int): IO[Either[ModelThrowable, BlockSigned]] =
        blocks.find { case (_, b) => b.message.sequenceNumber == seqNumber }.map(_._2).toRight(EntityNotFound).pure[IO]

      override def createGenesisBlock(): IO[Either[ModelThrowable, BlockSigned]] = ???

      override def getTransaction(transactionId: TransactionId): IO[Either[ModelThrowable, Transaction]] =
        transactions(transactionId).message.asRight[ModelThrowable].pure[IO]

      override def getTransactionByAccount(accountId: AddressId): IO[Either[ModelThrowable, Vector[TransactionSigned]]] =
        (transactions.filter { case (_, t) => t.message.source == accountId }.values.toVector ++
          transactions.filter { case (_, t) => t.message.destination == accountId }.values.toVector).asRight[ModelThrowable].pure[IO]

      override def crateGiftTransaction(
          newAccount: AccountSigned
      ): IO[Either[ModelThrowable, TransactionSigned]] = ???
    }

}
