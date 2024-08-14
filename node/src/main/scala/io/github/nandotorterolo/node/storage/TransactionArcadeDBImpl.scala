package io.github.nandotorterolo.node.storage

import cats.effect.Async
import cats.implicits._
import com.arcadedb.database.Database
import com.arcadedb.graph.Vertex
import io.github.nandotorterolo.models._
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.node.interfaces.TransactionStorage
import io.github.nandotorterolo.node.storage.ArcadeDB._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import scodec.bits.ByteVector

class TransactionArcadeDBImpl[F[_]: Async](db: Database) extends TransactionStorage[F] {

  implicit def logger: Logger[F] = Slf4jLogger.getLogger[F]

  private def getTransactionVertex(transactionId: TransactionId): Option[Vertex] = {
    val r = db.lookupByKey(TransactionVertexName, "key", transactionId.value.toBase58)
    if (r.hasNext) r.next().asVertex(true).some else None
  }

  override def get(transactionId: TransactionId): F[Either[ModelThrowable, Transaction]] = {
    Async[F]
      .delay {
        val r = db.lookupByKey(TransactionVertexName, "key", transactionId.value.toBase58)
        if (r.hasNext) {
          val vertex = r.next().asVertex(true)
          Transaction(
            source = AddressId(ByteVector.fromValidBase58(vertex.getString("source"))),
            destination = AddressId(ByteVector.fromValidBase58(vertex.getString("destination"))),
            amount = vertex.getDouble("amount"),
            nonce = vertex.getInteger("nonce")
          ).asRight[ModelThrowable]

        } else {
          (Message("Block Not Found"): ModelThrowable).asLeft[Transaction]
        }
      }
      .recover { case th => (Message(s"Something went wrong $th"): ModelThrowable).asLeft[Transaction] }
  }

}

object TransactionArcadeDBImpl {
  def build[F[_]: Async](db: Database) = new TransactionArcadeDBImpl[F](db)
}
