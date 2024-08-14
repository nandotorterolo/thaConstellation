package io.github.nandotorterolo.node.storage

import cats.effect.Async
import cats.implicits._
import com.arcadedb.database.Database
import com.arcadedb.graph.Vertex
import io.github.nandotorterolo.models.Block
import io.github.nandotorterolo.models.BlockId
import io.github.nandotorterolo.models.BlockSigned
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.models.TransactionId
import io.github.nandotorterolo.models.TransactionSigned
import io.github.nandotorterolo.node.interfaces.BlocksStorage
import io.github.nandotorterolo.node.service.AccountsUpdaterImpl
import io.github.nandotorterolo.node.storage.ArcadeDB.AccountVertexName
import io.github.nandotorterolo.node.storage.ArcadeDB.BlockVertexName
import io.github.nandotorterolo.node.storage.ArcadeDB.InBlockEdgeName
import io.github.nandotorterolo.node.storage.ArcadeDB.ParentEdgeName
import io.github.nandotorterolo.node.storage.ArcadeDB.TransactionVertexName
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import scodec.bits.ByteVector
import scodec.Attempt
import scodec.Codec

class BlocksArcadeDBImpl[F[_]: Async](db: Database) extends BlocksStorage[F] {

  implicit def logger: Logger[F] = Slf4jLogger.getLogger[F]

  override def insert(block: BlockSigned, txs: Vector[TransactionSigned]): F[Either[ModelThrowable, BlockSigned]] = {
    Async[F]
      .delay { BlockSigned.codec.encode(block) }
      .flatMap {
        case Attempt.Successful(value) =>
          Async[F]
            .blocking {
              db.begin()

              val blockVertex = db
                .newVertex(BlockVertexName)
                .set("key", block.hash.v.toBase58)
                .set("sequenceNumber", block.message.sequenceNumber)
                .set("priorBlock", block.message.priorBlock.value.toArray)
                .set("transactions", block.message.transactions.map(_.value.toBase58).mkString(","))
                .set("value", value.toByteArray)
                .save()

              if (!Block.isGenesisBlock(block.message)) {
                getBlockVertex(BlockId(block.message.priorBlock.value))
                  .map(parentBlock => blockVertex.newEdge(ParentEdgeName, parentBlock, true).save())
                  .orRaise(Message("Failed to fetch a block parent which was not theGenesis Block"))
                  .void
              }

              txs.foreach { tx =>
                val txVertex = db
                  .newVertex(TransactionVertexName)
                  .set("key", tx.hash.v.toBase58)
                  .set("source", tx.message.source.value.toBase58)
                  .set("destination", tx.message.destination.value.toBase58)
                  .set("amount", tx.message.amount)
                  .set("nonce", tx.message.nonce)
                  .set("value", value.toByteArray)
                  .set("block", blockVertex)
                  .save()
                txVertex.newEdge(InBlockEdgeName, blockVertex, true).save

              }

              // Update balance on account
              AccountsUpdaterImpl.balances(txs.map(_.message)).foreach {
                case (addressId, amount) =>
                  val account = db.lookupByKey(AccountVertexName, "key", addressId.value.toBase58)
                  if (account.hasNext) {
                    val b         = account.next().asVertex(true)
                    val oldAmount = b.getDouble("balance")
                    b.modify().set("balance", oldAmount + amount).save()
                  } else {
                    new IllegalStateException("Account should be there, rollback")
                  }
              }

              // Update nonces on account
              AccountsUpdaterImpl.nonces(txs.map(_.message)).foreach {
                case (addressId, nonce) =>
                  val account = db.lookupByKey(AccountVertexName, "key", addressId.value.toBase58)
                  if (account.hasNext) {
                    val b = account.next().asVertex(true)
                    b.modify().set("nonce", nonce).save()
                  } else {
                    new IllegalStateException("Account should be there, rollback")
                  }
              }

              db.commit()
              block.asRight[ModelThrowable]
            }
            .recoverWith {
              case th: Throwable =>
//                db.rollback()
                (Message("Error saving on db"): ModelThrowable)
                  .asLeft[BlockSigned]
                  .pure[F]
                  .flatTap(_ => Logger[F].error(th.getMessage))
            }
        case Attempt.Failure(cause) =>
          (Message("Error saving on db, decoding entity"): ModelThrowable)
            .asLeft[BlockSigned]
            .pure[F]
            .flatTap(_ => Logger[F].warn(cause.message))
      }
  }

  override def getAtSequenceNumber(sequenceNumber: Int): F[Option[BlockSigned]] = {
    Async[F].delay {
      val res = db.query("sql", s"SELECT FROM $BlockVertexName WHERE sequenceNumber = ? LIMIT 1", sequenceNumber)
      if (res.hasNext) {
        val value = res.next().getProperty[Array[Byte]]("value")
        Codec[BlockSigned].decode(ByteVector(value).bits).map(_.value).toOption
      } else {
        None
      }

    }
  }

  private def getBlockVertex(blockId: BlockId): Option[Vertex] = {
    val r = db.lookupByKey(BlockVertexName, "key", blockId.value.toBase58)
    if (r.hasNext) r.next().asVertex(true).some else None
  }

  override def get(blockId: BlockId): F[Either[ModelThrowable, Block]] = {
    Async[F]
      .delay {
        val r = db.lookupByKey(BlockVertexName, "key", blockId.value.toBase58)
        if (r.hasNext) {
          val vertex = r.next().asVertex(true)
          Block(
            priorBlock = BlockId(ByteVector(vertex.getBinary("priorBlock"))),
            sequenceNumber = vertex.getInteger("sequenceNumber"),
            transactions = vertex
              .getString("transactions")
              .split(",")
              .map(ByteVector.fromValidBase58(_))
              .map(TransactionId(_))
              .toVector
          ).asRight[ModelThrowable]

        } else {
          (Message("Block Not Found"): ModelThrowable).asLeft[Block]
        }
      }
      .recover { case th => (Message(s"Something went wrong $th"): ModelThrowable).asLeft[Block] }
  }

  override def close(): F[Unit] = Async[F].delay(db.close())

  override def isEmpty: F[Boolean] = getAtSequenceNumber(0).map(_.isEmpty)

  override def getHeight: F[Int] = Async[F].delay {
    val res = db.query("sql", s"SELECT max(sequenceNumber) AS sequenceNumber FROM $BlockVertexName")
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
