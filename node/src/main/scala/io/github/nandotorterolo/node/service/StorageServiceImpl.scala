package io.github.nandotorterolo.node.service

import cats.data.EitherT
import cats.effect.Async
import cats.implicits._
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.models._
import io.github.nandotorterolo.models.ModelThrowable.ConflictMessage
import io.github.nandotorterolo.models.ModelThrowable.EntityNotFound
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.node.interfaces._

class StorageServiceImpl[F[_]: Async](
    cripto: Cripto[F],
    accountsStorage: AccountsStorage[F],
    blocksStorage: BlocksStorage[F],
    transactionStorage: TransactionStorage[F],
    serverCredentials: ServerCredentials[F]
) extends StorageService[F] {

  override val getServerAccount: F[Either[ModelThrowable, Account]] =
    (for {
      publicKeySever <- EitherT(serverCredentials.getPublicKey)
      accountId = Address(publicKeySever).addressId
      account <- EitherT.fromOptionF(
        accountsStorage.get(accountId),
        Message("error, server account should be there"): ModelThrowable
      )
    } yield account).value

  /**
   * When a account is registered, a new transaction is created with a gift of 100
   */
  def crateGiftTransaction(
      newAccount: AccountSigned
  ): F[Either[ModelThrowable, TransactionSigned]] = {
    (for {
      privateKeyServer <- EitherT(serverCredentials.getPrivateKey)
      publicKeyServer  <- EitherT(serverCredentials.getPublicKey)
      serverAccount    <- EitherT(getServerAccount)

      source      = serverAccount.address.addressId
      destination = newAccount.message.address.addressId
      amount      = 100d                              // TODO to discuss it
      nonce       = serverAccount.latestUsedNonce + 1 // TODO Limitation of 1 Account registraion per slot period
      transaction = Transaction(source, destination, amount, nonce)

      transactionSigned <- EitherT(transaction.sign(privateKeyServer)(cripto))
      _                 <- EitherT(transactionSigned.validate[F](publicKeyServer)(cripto))

    } yield transactionSigned).value
  }

  /**
   * The server will have an account with a lot of money
   * When a client account is registered, a transaction will be created from server -> clientAccount
   * @return
   */
  def createServerAccount(): F[Either[ModelThrowable, AccountSigned]] = {
    val r = for {

      privateKey <- EitherT(serverCredentials.getPrivateKey)
      publicKey  <- EitherT(serverCredentials.getPublicKey)

      address = Address(publicKey)
      account = Account(address = address, balance = 1000000000d, latestUsedNonce = 0)
      accountSigned <- EitherT(account.sign(privateKey)(cripto))

      _ <- EitherT(accountSigned.validate[F](publicKey)(cripto))
        .flatMap(b => EitherT.cond[F](b, (), Message("SignatureValidation"): ModelThrowable))

      account <- EitherT(saveAccount(accountSigned))
    } yield account
    r.value
  }

  override def createGenesisBlock(): F[Either[ModelThrowable, BlockSigned]] = {

    val r = for {
      privateKey <- EitherT(serverCredentials.getPrivateKey)
      publicKey  <- EitherT(serverCredentials.getPublicKey)

      block = Block.unsignedGenesisBlock
      blockSigned <- EitherT(block.sign(privateKey)(cripto))
      _ <- EitherT(blockSigned.validate[F](publicKey)(cripto))
        .flatMap(b => EitherT.cond[F](b, (), Message("SignatureValidation"): ModelThrowable))

      block <- EitherT(saveBlock(blockSigned, Vector.empty))
    } yield block
    r.value
  }

  override def saveBlock(block: BlockSigned, txs: Vector[TransactionSigned]): F[Either[ModelThrowable, BlockSigned]] = {
    blocksStorage.insert(block, txs)
  }

  override def getBlockHead: F[Either[ModelThrowable, BlockSigned]] =
    blocksStorage.getHeight.flatMap(h => blocksStorage.getAtSequenceNumber(h).map(_.toRight(Message("Head unreachable"))))

  override def getBlock(blockId: BlockId): F[Either[ModelThrowable, BlockSigned]] =
    blocksStorage.get(blockId)

  override def getBlockBySeqNumber(seqNumber: Int): F[Either[ModelThrowable, BlockSigned]] =
    blocksStorage.getAtSequenceNumber(seqNumber).map(_.toRight(EntityNotFound))

  override def saveAccount(accountSigned: AccountSigned): F[Either[ModelThrowable, AccountSigned]] =
    accountsStorage.contains(accountSigned.message.address.addressId).flatMap {
      case true => (ConflictMessage("ExistingAddress"): ModelThrowable).asLeft[AccountSigned].pure[F]
      case false =>
        accountsStorage.insert(accountSigned).map {
          case true  => accountSigned.asRight[ModelThrowable]
          case false => (Message("Save issues"): ModelThrowable).asLeft[AccountSigned]
        }
    }

  override def getAccount(accountId: AddressId): F[Option[Account]] = {
    accountsStorage.get(accountId)
  }

  override def getTransactionByAccount(accountId: AddressId): F[Either[ModelThrowable, Vector[TransactionSigned]]] = {
    (
      accountsStorage.getSourceTransactions(accountId),
      accountsStorage.getDestinationTransactions(accountId)
    ).mapN(_ ++ _).attempt.map(_.leftMap(th => Message(s"Error Service ${th.getMessage}"): ModelThrowable))

  }

  def getTransaction(transactionId: TransactionId): F[Either[ModelThrowable, Transaction]] =
    transactionStorage.get(transactionId)

}

object StorageServiceImpl {
  def build[F[_]: Async](
      cripto: Cripto[F],
      accountsStorage: AccountsStorage[F],
      blockStorage: BlocksStorage[F],
      transactionStorage: TransactionStorage[F],
      serverCredentials: ServerCredentials[F]
  ): StorageService[F] =
    new StorageServiceImpl(cripto, accountsStorage, blockStorage, transactionStorage, serverCredentials)

}
