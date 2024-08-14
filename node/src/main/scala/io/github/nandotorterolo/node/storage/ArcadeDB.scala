package io.github.nandotorterolo.node.storage

import cats.effect.Sync
import cats.implicits._
import com.arcadedb.database.Database
import com.arcadedb.schema
import io.github.nandotorterolo.node.storage.ArcadeDB.AccountVertexName
import io.github.nandotorterolo.node.storage.ArcadeDB.BlockVertexName
import io.github.nandotorterolo.node.storage.ArcadeDB.InBlockEdgeName
import io.github.nandotorterolo.node.storage.ArcadeDB.ParentEdgeName
import io.github.nandotorterolo.node.storage.ArcadeDB.TransactionVertexName

class ArcadeDB[F[_]: Sync] {

  // todo move to config

  private def createAccountSchema(db: Database): F[Either[Throwable, schema.VertexType]] =
    Sync[F].blocking {
      val dbSchema      = db.getSchema
      val accountVertex = dbSchema.getOrCreateVertexType(AccountVertexName)
      accountVertex.getOrCreateProperty("key", schema.Type.STRING)
      accountVertex.getOrCreateProperty("balance", schema.Type.DOUBLE)
      accountVertex.getOrCreateProperty("latestUsedNonce", schema.Type.INTEGER)
      accountVertex.getOrCreateProperty("address", schema.Type.BINARY)
      accountVertex.getOrCreateProperty("publicKey", schema.Type.BINARY)
      accountVertex.getOrCreateProperty("value", schema.Type.BINARY)
      accountVertex.getOrCreateTypeIndex(schema.Schema.INDEX_TYPE.LSM_TREE, true, "key")
      accountVertex
    }.attempt

  private def createBlockSchema(db: Database): F[Either[Throwable, schema.VertexType]] =
    Sync[F].blocking {
      val dbSchema      = db.getSchema
      val accountVertex = dbSchema.getOrCreateVertexType(BlockVertexName)
      accountVertex.getOrCreateProperty("key", schema.Type.STRING)
      accountVertex.getOrCreateProperty("sequenceNumber", schema.Type.INTEGER)
      accountVertex.getOrCreateProperty("priorBlock", schema.Type.BINARY)
      accountVertex.getOrCreateProperty("transactions", schema.Type.STRING)
      accountVertex.getOrCreateProperty("value", schema.Type.BINARY)
      accountVertex.getOrCreateTypeIndex(schema.Schema.INDEX_TYPE.LSM_TREE, true, "key")
      accountVertex
    }.attempt

  private def createTransactionSchema(db: Database): F[Either[Throwable, schema.VertexType]] =
    Sync[F].blocking {
      val dbSchema      = db.getSchema
      val accountVertex = dbSchema.getOrCreateVertexType(TransactionVertexName)
      accountVertex.getOrCreateProperty("key", schema.Type.STRING)
      accountVertex.getOrCreateProperty("source", schema.Type.STRING)
      accountVertex.getOrCreateProperty("destination", schema.Type.STRING)
      accountVertex.getOrCreateProperty("amount", schema.Type.DOUBLE)
      accountVertex.getOrCreateProperty("nonce", schema.Type.INTEGER)
      accountVertex.getOrCreateProperty("value", schema.Type.BINARY)
      accountVertex.getOrCreateTypeIndex(schema.Schema.INDEX_TYPE.LSM_TREE, true, "key")
      accountVertex
    }.attempt

  private def createEdges(db: Database) = {
    Sync[F].blocking {
      val dbSchema = db.getSchema
      dbSchema.createEdgeType(ParentEdgeName)
      dbSchema.createEdgeType(InBlockEdgeName)
    }.attempt
  }

  def createSchemas(db: Database): F[Unit] = {
    for {
      _ <- createAccountSchema(db)
      _ <- createBlockSchema(db)
      _ <- createTransactionSchema(db)
      _ <- createEdges(db)
    } yield ()
  }

}

object ArcadeDB {
  def make[F[_]: Sync](): ArcadeDB[F] = {
    new ArcadeDB[F]
  }

  val DbName = "Node"
  val DbPath = s"../ArcadeDb/$DbName"

  // Vertex
  val AccountVertexName     = "Account"
  val BlockVertexName       = "Block"
  val TransactionVertexName = "Transaction"

  // Edges
  val ParentEdgeName  = "parentAt"
  val InBlockEdgeName = "blockAt"
}
