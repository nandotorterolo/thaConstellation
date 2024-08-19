package io.github.nandotorterolo.node.storage

import cats.effect.Async
import cats.implicits._
import com.arcadedb.database.Database
import com.arcadedb.graph.Vertex
import io.github.nandotorterolo.models.Account
import io.github.nandotorterolo.models.AccountSigned
import io.github.nandotorterolo.models.AddressId
import io.github.nandotorterolo.models.TransactionSigned
import io.github.nandotorterolo.node.interfaces.AccountsStorage
import io.github.nandotorterolo.node.storage.arcadeDB.vertex.AccountVertex
import io.github.nandotorterolo.node.storage.arcadeDB.vertex.TransactionVertex
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

class AccountsArcadeDBImpl[F[_]: Async](db: Database) extends AccountsStorage[F] {

  implicit def logger: Logger[F] = Slf4jLogger.getLogger[F]

  override def insert(accountSigned: AccountSigned): F[Boolean] = {

    Async[F]
      .delay {
        db.begin()
        AccountVertex.encode(accountSigned)(db).save()
        db.commit()
      }
      .map(_ => true)
      .recoverWith {
        case ex: Exception =>
          db.rollback()
          logger.warn(s"ex $ex").flatMap(_ => false.pure[F])
      }
  }

  override def contains(id: AddressId): F[Boolean] =
    AccountVertex.lookup(id.value.toBase58)(db).map(_.isDefined)

  override def get(id: AddressId): F[Option[Account]] =
    AccountVertex.lookup(id.value.toBase58)(db).map(_.map(_.message))

  override def getSourceTransactions(id: AddressId): F[Vector[TransactionSigned]] = {
    Async[F].delay {
      val query = db.query(
        "sql",
        s"MATCH {type: Account, as:a, where: (key ='${id.value.toBase58}')}.in('hasSource'){type: Transaction, as: transaction} RETURN transaction"
      )
      val response = new scala.collection.mutable.ArrayBuffer[TransactionSigned]()
      // todo handle as Stream
      while (query.hasNext) {
        response.addOne(TransactionVertex.decode(query.next().getProperty[Vertex]("transaction")))
      }
      response.toVector
    }

  }

  override def getDestinationTransactions(id: AddressId): F[Vector[TransactionSigned]] = {
    Async[F].delay {
      val query = db.query(
        "sql",
        s"MATCH {type: Account, as:a, where: (key ='${id.value.toBase58}')}.in('hasDestination'){type: Transaction, as: transaction} RETURN transaction"
      )
      val response = new scala.collection.mutable.ArrayBuffer[TransactionSigned]()
      // todo handle as Stream
      while (query.hasNext) {
        response.addOne(TransactionVertex.decode(query.next().getProperty[Vertex]("transaction")))
      }
      response.toVector
    }

  }
}

object AccountsArcadeDBImpl {
  def build[F[_]: Async](db: Database) = new AccountsArcadeDBImpl[F](db)
}
