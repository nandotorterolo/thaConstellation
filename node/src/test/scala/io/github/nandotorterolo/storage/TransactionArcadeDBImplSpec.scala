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
import io.github.nandotorterolo.node.storage.arcadeDB.dsl.SchemaFactory
import io.github.nandotorterolo.node.storage.arcadeDB.vertex.TransactionVertex
import io.github.nandotorterolo.node.storage.TransactionArcadeDBImpl
import munit.AnyFixture
import munit.CatsEffectSuite
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

class TransactionArcadeDBImplSpec extends CatsEffectSuite {

  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]
  val cripto: Cripto[IO]                     = EcdsaBCEncryption.build[IO]

  private val dbFixtureResource = ResourceSuiteLocalFixture(
    "dbFixture",
    Resource.make {
      val database = new DatabaseFactory("/tmp/ArcadeDb/TransactionTest").create()
      SchemaFactory.make[IO](database.getSchema).map(_ => database)
    } { db => Async[IO].delay(db.drop()) }
  )

  override def munitFixtures: Seq[AnyFixture[Database]] = List(dbFixtureResource)

  test("Get Transaction") {

    for {
      kp <- cripto.getKeyPair.rethrow

      database = dbFixtureResource()

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

      txFromDb = new TransactionArcadeDBImpl[IO](database).get(TransactionId(transactionSigned.hash.value))

      _ <- assertIO(txFromDb, Right(transaction).withLeft[ModelThrowable])

    } yield ()
  }
}
