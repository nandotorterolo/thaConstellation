package io.github.nandotorterolo.storage

import cats.effect.kernel.Async
import cats.effect.IO
import cats.effect.Resource
import cats.effect.Sync
import com.arcadedb.database.Database
import com.arcadedb.database.DatabaseFactory
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models._
import io.github.nandotorterolo.node.interfaces.AccountsStorage
import io.github.nandotorterolo.node.interfaces.BlocksStorage
import io.github.nandotorterolo.node.storage.arcadeDB.dsl.SchemaFactory
import io.github.nandotorterolo.node.storage.arcadeDB.vertex.AccountVertex
import io.github.nandotorterolo.node.storage.arcadeDB.vertex.BlockVertex
import io.github.nandotorterolo.node.storage.AccountsArcadeDBImpl
import io.github.nandotorterolo.node.storage.BlocksArcadeDBImpl
import munit.AnyFixture
import munit.CatsEffectSuite
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import scodec.bits.ByteVector

class BlocksArcadeDBImplSpec extends CatsEffectSuite {

  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  private val dbFixtureResource = ResourceSuiteLocalFixture(
    "dbFixture",
    Resource.make {
      val database = new DatabaseFactory("/tmp/ArcadeDb/BlockTest").create()
      SchemaFactory.make[IO](database.getSchema).map(_ => database)
    } { db => Async[IO].delay(db.drop()) }
  )

  override def munitFixtures: Seq[AnyFixture[Database]] = List(dbFixtureResource)

  test("Get Block") {

    for {
      kpSource      <- cripto.getKeyPair.rethrow
      kpDestination <- cripto.getKeyPair.rethrow

      database = dbFixtureResource()

      transaction = Transaction(
        source = Address(kpSource.getPublic).addressId,
        destination = Address(kpDestination.getPublic).addressId,
        amount = 100d,
        nonce = 1
      )
      transactionSigned <- transaction.sign(kpSource.getPrivate)(cripto).rethrow

      block = Block(
        priorBlock = BlockId(ByteVector.fill(64)(1.toByte)),
        sequenceNumber = 5,
        transactions = Vector(TransactionId(transactionSigned.hash.value))
      )
      blockSigned <- block.sign(kpSource.getPrivate)(cripto).rethrow

      _ = database.begin()
      _ = BlockVertex.encode(blockSigned)(database).save()
      _ = database.commit()

      blockStorage: BlocksStorage[IO] = new BlocksArcadeDBImpl[IO](database)

      _ <- assertIO(blockStorage.get(BlockId(blockSigned.hash.value)), Right(blockSigned).withLeft[ModelThrowable])
      _ <- assertIO(blockStorage.getAtSequenceNumber(5), Option(blockSigned))
      _ <- assertIO(blockStorage.getHeight, 5)

    } yield ()
  }

  test("insert Genesis and next block") {
    for {
      kpServer      <- cripto.getKeyPair.rethrow
      kpSource      <- cripto.getKeyPair.rethrow
      kpDestination <- cripto.getKeyPair.rethrow

      database = dbFixtureResource()

      // Save Accounts
      serverAccount = Account(Address(kpServer.getPublic), 1000, 0)
      serverAccountSigned <- serverAccount.sign(kpServer.getPrivate)(cripto).rethrow

      sourceAccount = Account(Address(kpSource.getPublic), 100, 0)
      sourceAccountSigned <- sourceAccount.sign(kpSource.getPrivate)(cripto).rethrow

      destinationAccount = Account(Address(kpDestination.getPublic), 100, 0)
      destinationAccountSigned <- destinationAccount.sign(kpDestination.getPrivate)(cripto).rethrow

      _ = database.begin()
      _ = AccountVertex.encode(serverAccountSigned)(database).save()
      _ = AccountVertex.encode(sourceAccountSigned)(database).save()
      _ = AccountVertex.encode(destinationAccountSigned)(database).save()
      _ = database.commit()
      // end save accounts

      // save block 0
      blockGenesis = Block(priorBlock = BlockId(ByteVector.fill(64)(0.toByte)), sequenceNumber = 0, transactions = Vector.empty)
      blockGenesisSigned <- blockGenesis.sign(kpServer.getPrivate)(cripto).rethrow

      blockStorage: BlocksStorage[IO]      = new BlocksArcadeDBImpl[IO](database)
      accountsStorage: AccountsStorage[IO] = new AccountsArcadeDBImpl[IO](database)

      _ <- assertIO(blockStorage.insert(blockGenesisSigned, Vector.empty), Right(blockGenesisSigned).withLeft[ModelThrowable])
      _ <- assertIO(blockStorage.get(BlockId(blockGenesisSigned.hash.value)).map(_.map(_.message.sequenceNumber)), Right(0).withLeft[ModelThrowable])

      // Save block 1
      transaction = Transaction(
        source = Address(kpSource.getPublic).addressId,
        destination = Address(kpDestination.getPublic).addressId,
        amount = 1d,
        nonce = 2
      )
      transactionSigned <- transaction.sign(kpSource.getPrivate)(cripto).rethrow

      block = Block(
        priorBlock = BlockId(blockGenesisSigned.hash.value),
        sequenceNumber = 1,
        transactions = Vector(TransactionId(transactionSigned.hash.value))
      )
      blockSigned <- block.sign(kpServer.getPrivate)(cripto).rethrow

      _ <- assertIO(blockStorage.insert(blockSigned, Vector(transactionSigned)), Right(blockSigned).withLeft[ModelThrowable])
      _ <- assertIO(blockStorage.get(BlockId(blockSigned.hash.value)).map(_.map(_.message.sequenceNumber)), Right(1).withLeft[ModelThrowable])

      // validate accounts
      _ <- assertIO(accountsStorage.get(sourceAccount.address.addressId).map(_.map(_.balance)), Some(99d))
      _ <- assertIO(accountsStorage.get(destinationAccount.address.addressId).map(_.map(_.balance)), Some(101d))

      _ <- assertIO(accountsStorage.getSourceTransactions(sourceAccount.address.addressId).map(_.size), 1)
      _ <- assertIO(accountsStorage.getSourceTransactions(sourceAccount.address.addressId).map(_.head).map(_.message.amount), 1d)

      _ <- assertIO(accountsStorage.getDestinationTransactions(destinationAccount.address.addressId).map(_.head).map(_.message.amount), 1d)
    } yield ()
  }
}
