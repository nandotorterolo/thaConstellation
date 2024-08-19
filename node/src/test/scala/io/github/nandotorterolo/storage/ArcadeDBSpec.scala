package io.github.nandotorterolo.storage

import cats.effect.kernel.Async
import cats.effect.IO
import cats.effect.Resource
import cats.effect.Sync
import com.arcadedb.database.Database
import com.arcadedb.database.DatabaseFactory
import com.arcadedb.graph.Vertex
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models.Account
import io.github.nandotorterolo.models.Address
import io.github.nandotorterolo.models.AddressId
import io.github.nandotorterolo.models.Block
import io.github.nandotorterolo.models.BlockId
import io.github.nandotorterolo.models.Transaction
import io.github.nandotorterolo.models.TransactionId
import io.github.nandotorterolo.node.storage.arcadeDB.dsl.SchemaFactory
import io.github.nandotorterolo.node.storage.arcadeDB.dsl.SchemaFactory.EdgeNames
import io.github.nandotorterolo.node.storage.arcadeDB.vertex.AccountVertex
import io.github.nandotorterolo.node.storage.arcadeDB.vertex.BlockVertex
import io.github.nandotorterolo.node.storage.arcadeDB.vertex.TransactionVertex
import munit.AnyFixture
import munit.CatsEffectSuite
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import scodec.bits.ByteVector

class ArcadeDBSpec extends CatsEffectSuite {

  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]
  val cripto: Cripto[IO]                     = EcdsaBCEncryption.build[IO]

  /**
   * the database will be drop at the end of the test
   */
  private val dbFixtureResource = ResourceSuiteLocalFixture(
    "dbFixture",
    Resource.make {
      val database = new DatabaseFactory("/tmp/ArcadeDb/NodeTest").create()
      SchemaFactory.make[IO](database.getSchema).map(_ => database)
    } { db => Async[IO].delay(db.drop()) }
  )

  override def munitFixtures: Seq[AnyFixture[Database]] = List(dbFixtureResource)

  test("schema Account") {

    for {
      kp <- cripto.getKeyPair.rethrow

      database = dbFixtureResource()
      schema   = database.getSchema
      _ <- assertIOBoolean(Async[IO].delay(schema.existsType("Account")))

      fakePublicKey = ByteVector.fill(170)(0)
      account       = Account(Address(AddressId("JpE3CyJtqsJ35cE6U1uq7RKXLAg"), fakePublicKey), 100d, 1)
      accountSigned <- account.sign(kp.getPrivate)(cripto).rethrow

      _ = database.begin()
      _ = AccountVertex.encode(accountSigned)(database).save()
      _ = database.commit()

      accountFromDB = AccountVertex.lookup[IO](account.address.addressId.value.toBase58)(database)
      _ <- assertIOBoolean(accountFromDB.map(_.isDefined))

    } yield ()
  }

  test("schema Transaction") {

    for {
      kp <- cripto.getKeyPair.rethrow

      database = dbFixtureResource()
      schema   = database.getSchema
      _ <- assertIOBoolean(Async[IO].delay(schema.existsType("Transaction")))

      transaction = Transaction(
        source = AddressId("JpE3CyJtqsJ35cE6U1uq7RKXLAg"),
        destination = AddressId("3FTBbJzQptLTbNVZyCvNQeScRcSQ"),
        amount = 100d,
        nonce = 1
      )
      transactionSigned <- transaction.sign(kp.getPrivate)(cripto).rethrow

      _ = database.begin()
      _ = TransactionVertex.encode(transactionSigned)(database).save()
      _ = database.commit()

      transactionFromDB = TransactionVertex.lookup[IO](transactionSigned.hash.value.toBase58)(database)
      _ <- assertIOBoolean(transactionFromDB.map(_.isDefined))

    } yield ()
  }

  test("schema Block") {

    for {
      kp <- cripto.getKeyPair.rethrow

      database = dbFixtureResource()
      schema   = database.getSchema
      _ <- assertIOBoolean(Async[IO].delay(schema.existsType("Block")))

      transaction = Transaction(
        source = AddressId("JpE3CyJtqsJ35cE6U1uq7RKXLAg"),
        destination = AddressId("3FTBbJzQptLTbNVZyCvNQeScRcSQ"),
        amount = 100d,
        nonce = 1
      )
      transactionSigned <- transaction.sign(kp.getPrivate)(cripto).rethrow
      block_A = Block(
        priorBlock = BlockId(ByteVector.fill(64)(0.toByte)),
        sequenceNumber = 1,
        transactions = Vector(TransactionId(transactionSigned.hash.value))
      )
      blockSigned_A <- block_A.sign(kp.getPrivate)(cripto).rethrow

      block_B = Block(
        priorBlock = BlockId(ByteVector.fill(64)(0.toByte)),
        sequenceNumber = 2,
        transactions = Vector(TransactionId(transactionSigned.hash.value))
      )
      blockSigned_B <- block_B.sign(kp.getPrivate)(cripto).rethrow

      _ = database.begin()
      _ = BlockVertex.encode(blockSigned_A)(database).save()
      _ = BlockVertex.encode(blockSigned_B)(database).save()

      blockVertexA <- BlockVertex.lookupVertex[IO](blockSigned_A.hash.value.toBase58)(database).rethrow
      blockVertexB <- BlockVertex.lookupVertex[IO](blockSigned_B.hash.value.toBase58)(database).rethrow
      _ = blockVertexB.newEdge(SchemaFactory.EdgeNames.ParentEdgeName, blockVertexA, true).save()

      _ = database.commit()

      // return a block and it parents,  result.getPropertyNames // [block, parent]
      res = database.query(
        "sql",
        s"MATCH {type: Block, as:block, where: (key ='${blockSigned_B.hash.value.toBase58}')}.out('parentAt'){type: Block, as: parent} RETURN block, parent"
      )

      // be careful with sized of the iterator, and decoding
      result   = res.next()
      vertex_b = result.getProperty[Vertex]("block")  // preload
      vertex_a = result.getProperty[Vertex]("parent") // this was not preload

      block_b_fromDb = BlockVertex.decode(vertex_b)
      block_a_fromDb = BlockVertex.decode(vertex_a)

      _ = assert(block_a_fromDb.message.sequenceNumber == 1)
      _ = assert(block_b_fromDb.message.sequenceNumber == 2)

    } yield ()
  }

  test("schema Fetch Transactions with match queries") {

    for {
      kpAccountA <- cripto.getKeyPair.rethrow
      kpAccountB <- cripto.getKeyPair.rethrow

      database = dbFixtureResource()

      addressA = Address(kpAccountA.getPublic)
      accountA = Account(address = addressA, balance = 100, latestUsedNonce = 1)
      accountASigned <- accountA.sign(kpAccountA.getPrivate)(cripto).rethrow

      addressB = Address(kpAccountB.getPublic)
      accountB = Account(address = addressB, balance = 200, latestUsedNonce = 5)
      accountBSigned <- accountB.sign(kpAccountB.getPrivate)(cripto).rethrow

      transaction_1 = Transaction(addressA.addressId, addressB.addressId, 100d, 3)
      transaction_2 = Transaction(addressA.addressId, addressB.addressId, 100d, 4)
      transactionSigned_1 <- transaction_1.sign(kpAccountA.getPrivate)(cripto).rethrow
      transactionSigned_2 <- transaction_2.sign(kpAccountA.getPrivate)(cripto).rethrow

      block = Block(
        priorBlock = BlockId(ByteVector.fill(64)(0.toByte)),
        sequenceNumber = 1,
        transactions = Vector(TransactionId(transactionSigned_1.hash.value), TransactionId(transactionSigned_2.hash.value))
      )
      blockSigned <- block.sign(kpAccountA.getPrivate)(cripto).rethrow

      _ = database.begin()

      _ = AccountVertex.encode(accountASigned)(database).save()
      _ = AccountVertex.encode(accountBSigned)(database).save()
      _ = BlockVertex.encode(blockSigned)(database).save()
      _ = TransactionVertex.encode(transactionSigned_1)(database).save()
      _ = TransactionVertex.encode(transactionSigned_2)(database).save()

      accountAVertex <- AccountVertex.lookupVertex[IO](accountASigned.message.address.addressId.value.toBase58)(database).rethrow
      accountBVertex <- AccountVertex.lookupVertex[IO](accountBSigned.message.address.addressId.value.toBase58)(database).rethrow
      blockVertex    <- BlockVertex.lookupVertex[IO](blockSigned.hash.value.toBase58)(database).rethrow
      txVertex_1     <- TransactionVertex.lookupVertex[IO](transactionSigned_1.hash.value.toBase58)(database).rethrow
      txVertex_2     <- TransactionVertex.lookupVertex[IO](transactionSigned_2.hash.value.toBase58)(database).rethrow

      _ = blockVertex.newEdge(EdgeNames.InBlockEdgeName, txVertex_1, true).save()
      _ = blockVertex.newEdge(EdgeNames.InBlockEdgeName, txVertex_2, true).save()

      _ = txVertex_1.newEdge(EdgeNames.hasSourceEdgeName, accountAVertex, true).save()
      _ = txVertex_1.newEdge(EdgeNames.hasDestinationEdgeName, accountBVertex, true).save()

      _ = txVertex_2.newEdge(EdgeNames.hasSourceEdgeName, accountAVertex, true).save()
      _ = txVertex_2.newEdge(EdgeNames.hasDestinationEdgeName, accountBVertex, true).save()

      _ = database.commit()

      /**
       * Givven a block, fetch all transaction vertex with edge blockAt
       * In this case return has 2 items, { transaction: #39:0[?] }, { transaction: #36:0[?] }
       * in out match query depends how from-to edge was cretated- if tx -> block, query should be in
       *
       *    _ = transactionVertex_1.newEdge(SchemaFactory.EdgeNames.InBlockEdgeName, blockVertex, true).save()
       *   _ = transactionVertex_2.newEdge(SchemaFactory.EdgeNames.InBlockEdgeName, blockVertex, true).save()
       *    s"MATCH {type: Block, as:block, where: (key ='${blockSigned.hash.value.toBase58}')}.in('blockAt'){type: Transaction, as: transaction} RETURN transaction"
       */
      queryA = database.query(
        "sql",
        s"MATCH {type: Block, as:block, where: (key ='${blockSigned.hash.value.toBase58}')}.out('blockAt'){type: Transaction, as: transaction} RETURN transaction"
      )

      _          = assert(queryA.hasNext)
      queryA_tx1 = TransactionVertex.decode(queryA.next().getProperty[Vertex]("transaction"))
      _          = assert(queryA_tx1.message.nonce == 4)

      _          = assert(queryA.hasNext)
      queryA_tx2 = TransactionVertex.decode(queryA.next().getProperty[Vertex]("transaction"))
      _          = assert(queryA_tx2.message.nonce == 3)

      _ = assert(!queryA.hasNext)

      /**
       * Query all transaction that has source Addres A
       */
      queryB = database.query(
        "sql",
        s"MATCH {type: Account, as:a, where: (key ='${accountASigned.message.address.addressId.value.toBase58}')}.in('hasSource'){type: Transaction, as: transaction} RETURN transaction"
      )

      _          = assert(queryB.hasNext)
      queryB_tx1 = TransactionVertex.decode(queryB.next().getProperty[Vertex]("transaction"))
      _          = assert(queryB_tx1.message.source.value.toBase58 == accountA.address.addressId.value.toBase58)

      _          = assert(queryB.hasNext)
      queryB_tx2 = TransactionVertex.decode(queryB.next().getProperty[Vertex]("transaction"))
      _          = assert(queryB_tx2.message.source.value.toBase58 == accountA.address.addressId.value.toBase58)

      _ = assert(!queryB.hasNext)

      /**
       * Query all transaction that has destination Addres B
       */
      queryC = database.query(
        "sql",
        s"MATCH {type: Account, as:a, where: (key ='${accountBSigned.message.address.addressId.value.toBase58}')}.in('hasDestination'){type: Transaction, as: transaction} RETURN transaction"
      )

      _          = assert(queryC.hasNext)
      queryC_tx1 = TransactionVertex.decode(queryC.next().getProperty[Vertex]("transaction"))
      _          = assert(queryC_tx1.message.destination.value.toBase58 == accountB.address.addressId.value.toBase58)

      _          = assert(queryC.hasNext)
      queryC_tx2 = TransactionVertex.decode(queryC.next().getProperty[Vertex]("transaction"))
      _          = assert(queryC_tx2.message.destination.value.toBase58 == accountB.address.addressId.value.toBase58)

      _ = assert(!queryC.hasNext)

    } yield ()
  }

}
