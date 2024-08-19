package io.github.nandotorterolo.node.storage

import cats.data.EitherT
import cats.effect.Async
import com.arcadedb.database.Database
import io.github.nandotorterolo.models._
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.node.interfaces.TransactionStorage
import io.github.nandotorterolo.node.storage.arcadeDB.vertex.TransactionVertex
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

class TransactionArcadeDBImpl[F[_]: Async](db: Database) extends TransactionStorage[F] {

  implicit def logger: Logger[F] = Slf4jLogger.getLogger[F]

  override def get(transactionId: TransactionId): F[Either[ModelThrowable, Transaction]] =
    EitherT
      .fromOptionF(
        TransactionVertex.lookup(transactionId.value.toBase58)(db),
        Message("Transaction Not Found"): ModelThrowable
      )
      .map(_.message)
      .value

}

object TransactionArcadeDBImpl {
  def build[F[_]: Async](db: Database) = new TransactionArcadeDBImpl[F](db)
}
