package io.github.nandotorterolo.storage

import scala.jdk.CollectionConverters._

import cats.effect.kernel.Async
import cats.effect.IO
import com.arcadedb.server.ArcadeDBServer
import com.arcadedb.server.ServerDatabase
import com.arcadedb.ContextConfiguration
import io.github.nandotorterolo.node.storage.ArcadeDB
import munit.CatsEffectSuite

class ArcadeDBSpec extends CatsEffectSuite {

  private val config = Map(
    "arcadedb.server.rootPassword"      -> "root1234",
    "arcadedb.server.databaseDirectory" -> "/tmp/ArcadeDb",
//    "Darcadedb.server.defaultDatabases" -> "Node[node:admin1234]"
  ).asJava.asInstanceOf[java.util.Map[String, Object]]


  private val arcadeDb: Fixture[ServerDatabase] = new Fixture[ServerDatabase]("db") {
    val dBServer: ArcadeDBServer         = new ArcadeDBServer(new ContextConfiguration(config))
    override def apply(): ServerDatabase = dBServer.getOrCreateDatabase("NodeTest")

    override def beforeAll(): Unit = {

      dBServer.start()
    }

    override def afterAll(): Unit = {
      dBServer.stop()
    }
  }

  override def munitFixtures: Seq[Fixture[ServerDatabase]] = List(arcadeDb)

  test("schema test") {

    for {
      db <- Async[IO].blocking(arcadeDb())
      arcade = ArcadeDB.make[IO]()
      _ <- arcade.createSchemas(db)

      _ <- assertIOBoolean(Async[IO].delay(db.getSchema.existsType("Account")))

    } yield ()
  }

}
