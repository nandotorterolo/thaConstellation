package io.github.nandotorterolo.node.storage

import scala.jdk.OptionConverters.RichOptional
import scala.util.Try

import cats.data.EitherT
import cats.effect.Async
import cats.implicits._
import com.arcadedb.database.Database
import com.arcadedb.query.sql.executor.EmptyResult
import com.arcadedb.query.sql.executor.ResultInternal
import io.github.nandotorterolo.models._
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.node.interfaces.BlocksStorage
import io.github.nandotorterolo.node.service.AccountsUpdaterImpl
import io.github.nandotorterolo.node.storage.arcadeDB.dsl.SchemaFactory.EdgeNames
import io.github.nandotorterolo.node.storage.arcadeDB.vertex.AccountVertex
import io.github.nandotorterolo.node.storage.arcadeDB.vertex.BlockVertex
import io.github.nandotorterolo.node.storage.arcadeDB.vertex.TransactionVertex
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

class BlocksArcadeDBImpl[F[_]: Async](db: Database) extends BlocksStorage[F] {

  implicit def logger: Logger[F] = Slf4jLogger.getLogger[F]

  override def insert(block: BlockSigned, txs: Vector[TransactionSigned]): F[Either[ModelThrowable, BlockSigned]] = {

    Async[F]
      .blocking {
        db.begin()

        val blockVertex = BlockVertex.encode(block)(db).save()

        // add edge relationship between block and parent
        if (!Block.isGenesisBlock(block.message)) {
          BlockVertex.lookupVertexE(block.message.priorBlock.value.toBase58)(db) match {
            case Right(parentBlock) => blockVertex.newEdge(EdgeNames.ParentEdgeName, parentBlock, true).save()
            case _                  => new IllegalStateException("Block should be there, rollback")
          }
        }

        // save tx, edges block <-> tx, edges tx <-> account
        txs.foreach { tx =>
          val txVertex = TransactionVertex.encode(tx)(db).save()
          txVertex.newEdge(EdgeNames.InBlockEdgeName, blockVertex, true).save

          AccountVertex.lookupVertexE(tx.message.source.value.toBase58)(db) match {
            case Right(sourceAccountVertex) =>
              txVertex.newEdge(EdgeNames.hasSourceEdgeName, sourceAccountVertex, true).save()
            case _ => new IllegalStateException("Account should be there, rollback")
          }
          AccountVertex.lookupVertexE(tx.message.destination.value.toBase58)(db) match {
            case Right(destAccountVertex) =>
              txVertex.newEdge(EdgeNames.hasDestinationEdgeName, destAccountVertex, true).save()
            case _ => new IllegalStateException("Account should be there, rollback")
          }

        }

        // Update balance on account
        AccountsUpdaterImpl.balances(txs.map(_.message)).foreach {
          case (addressId, amount) =>
            AccountVertex.lookupVertexE(addressId.value.toBase58)(db) match {
              case Right(vertex) =>
                val oldAmount = vertex.getDouble("balance")
                vertex.modify().set("balance", oldAmount + amount).save()
              case _ => new IllegalStateException("Account should be there, rollback")
            }
        }

        // Update nonces on account
        AccountsUpdaterImpl.nonces(txs.map(_.message)).foreach {
          case (addressId, nonce) =>
            AccountVertex.lookupVertexE(addressId.value.toBase58)(db) match {
              case Right(vertex) =>
                vertex.modify().set("latestUsedNonce", nonce).save()
              case _ => new IllegalStateException("Account should be there, rollback")
            }
        }

        db.commit()
        block.asRight[ModelThrowable]
      }
      .recoverWith {
        case th: Throwable =>
          db.rollback()
          (Message("Error saving on db"): ModelThrowable)
            .asLeft[BlockSigned]
            .pure[F]
            .flatTap(_ => Logger[F].error(th.getMessage))
      }

  }

  override def getAtSequenceNumber(sequenceNumber: Int): F[Option[BlockSigned]] = {
    Async[F].delay {
      val res = db.query("sql", s"SELECT FROM ${BlockVertex.vertexName} WHERE sequenceNumber = ? LIMIT 1", sequenceNumber)
      Try {
        res.nextIfAvailable() match {
          case _: EmptyResult           => None
          case internal: ResultInternal => internal.getVertex.toScala.map(BlockVertex.decode)
          case _                        => None
        }
      }.getOrElse(None)

    }
  }

  override def get(blockId: BlockId): F[Either[ModelThrowable, Block]] =
    EitherT
      .fromOptionF(
        BlockVertex.lookup(blockId.value.toBase58)(db),
        Message("Block Not Found"): ModelThrowable
      )
      .map(_.message)
      .value

  override def isEmpty: F[Boolean] = getAtSequenceNumber(0).map(_.isEmpty)

  override def getHeight: F[Int] = Async[F].delay {
    val res = db.query("sql", s"SELECT max(sequenceNumber) AS sequenceNumber FROM ${BlockVertex.vertexName}")
    if (res.hasNext) {
      val height = res.next().getProperty[Integer]("sequenceNumber")
      height
    } else {
      0
    }

  }
}

object BlocksArcadeDBImpl {
  def build[F[_]: Async](db: Database) = new BlocksArcadeDBImpl[F](db)
}
