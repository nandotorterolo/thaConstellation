package io.github.nandotorterolo

import cats.effect.IO
import cats.implicits._
import io.github.nandotorterolo.models._
import io.github.nandotorterolo.node.interfaces.StorageService

package object validators {

  def storageMock(
      accounts: Map[AddressId, Account] = Map.empty,
      blocks: Map[BlockId, Block] = Map.empty,
      transactions: Map[TransactionId, Transaction] = Map.empty,
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

      override def getBlock(blockId: BlockId): IO[Either[ModelThrowable, Block]] =
        blocks(blockId).asRight[ModelThrowable].pure[IO]

      override def createGenesisBlock(): IO[Either[ModelThrowable, BlockSigned]] = ???

      override def getTransaction(transactionId: TransactionId): IO[Either[ModelThrowable, Transaction]] =
        transactions(transactionId).asRight[ModelThrowable].pure[IO]

      override def crateGiftTransaction(
          newAccount: AccountSigned
      ): IO[Either[ModelThrowable, TransactionSigned]] = ???
    }

}
