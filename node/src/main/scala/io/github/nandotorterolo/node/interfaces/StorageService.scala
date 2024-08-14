package io.github.nandotorterolo.node.interfaces

import io.github.nandotorterolo.models.Account
import io.github.nandotorterolo.models.AccountSigned
import io.github.nandotorterolo.models.AddressId
import io.github.nandotorterolo.models.Block
import io.github.nandotorterolo.models.BlockId
import io.github.nandotorterolo.models.BlockSigned
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.Transaction
import io.github.nandotorterolo.models.TransactionId
import io.github.nandotorterolo.models.TransactionSigned

trait StorageService[F[_]] {

  // Operations for Accounts //

  def saveAccount(account: AccountSigned): F[Either[ModelThrowable, AccountSigned]]

  def getServerAccount: F[Either[ModelThrowable, Account]]

  def createServerAccount(): F[Either[ModelThrowable, AccountSigned]]

  def getAccount(accountId: AddressId): F[Option[Account]]

  // Operations for Blocks //

  def saveBlock(block: BlockSigned, txs: Vector[TransactionSigned]): F[Either[ModelThrowable, BlockSigned]]

  def getBlockHead: F[Either[ModelThrowable, BlockSigned]]

  def getBlock(blockId: BlockId): F[Either[ModelThrowable, Block]]

  // only used 1 time, the first time the node is created
  def createGenesisBlock(): F[Either[ModelThrowable, BlockSigned]]

  // Operations for Transactions //

  def getTransaction(transactionId: TransactionId): F[Either[ModelThrowable, Transaction]]

  // used for registration process, only valid for server source transaction
  def crateGiftTransaction(newAccount: AccountSigned): F[Either[ModelThrowable, TransactionSigned]]

}
