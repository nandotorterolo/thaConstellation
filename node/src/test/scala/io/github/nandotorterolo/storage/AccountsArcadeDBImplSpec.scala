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
import io.github.nandotorterolo.node.storage.AccountsArcadeDBImpl
import munit.AnyFixture
import munit.CatsEffectSuite
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

class AccountsArcadeDBImplSpec extends CatsEffectSuite {

  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  private val dbFixtureResource = ResourceSuiteLocalFixture(
    "dbFixture",
    Resource.make {
      val database = new DatabaseFactory("/tmp/ArcadeDb/accountTest").create()
      SchemaFactory.make[IO](database.getSchema).map(_ => database)
    } { db => Async[IO].delay(db.drop()) }
  )

  override def munitFixtures: Seq[AnyFixture[Database]] = List(dbFixtureResource)

  test("Get Account") {

    for {
      kp <- cripto.getKeyPair.rethrow

      database = dbFixtureResource()

      account = Account(
        address = Address(kp.getPublic),
        balance = 100d,
        latestUsedNonce = 1
      )
      accountSigned <- account.sign(kp.getPrivate)(cripto).rethrow

      accountFromDb = new AccountsArcadeDBImpl[IO](database)

      accountId = accountSigned.message.address.addressId.value
      _ <- assertIOBoolean(accountFromDb.insert(accountSigned))
      _ <- assertIOBoolean(accountFromDb.contains(AddressId(accountId)))
      _ <- assertIO(accountFromDb.get(AddressId(accountId)), Some(account))

    } yield ()
  }
}
