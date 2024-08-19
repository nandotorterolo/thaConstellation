package io.github.nandotorterolo.node.storage.arcadeDB.dsl

import scala.util.Try

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import com.arcadedb.schema.Schema
import com.arcadedb.schema.Type
import io.github.nandotorterolo.node.storage.arcadeDB.vertex.AccountVertex
import io.github.nandotorterolo.node.storage.arcadeDB.vertex.BlockVertex
import io.github.nandotorterolo.node.storage.arcadeDB.vertex.TransactionVertex
import org.typelevel.log4cats.Logger

object SchemaFactory {

  object EdgeNames {
    val ParentEdgeName         = "parentAt"
    val InBlockEdgeName        = "blockAt"
    val hasSourceEdgeName      = "hasSource"
    val hasDestinationEdgeName = "hasDestination"
  }

  private val vertices: Seq[VertexDsl[?]] = Seq(
    AccountVertex.make,
    TransactionVertex.make,
    BlockVertex.make
  )

  private def createVertex(vertexDsl: VertexDsl[?], schema: Schema): Either[Throwable, Unit] = {
    Try {
      val vertex = schema.getOrCreateVertexType(vertexDsl.name)
      vertexDsl.properties.foreach { property =>
        vertex
          .getOrCreateProperty(property.name, property.schemaType)

      }
      vertexDsl.indices.foreach { index =>
        vertex
          .getOrCreateTypeIndex(index.indexType, index.unique, index.propertyName)
      }
    }
  }.toEither

  private def createEdges(schema: Schema): Either[Throwable, Unit] = {
    Try {

      // block <-> block
      val edgeBlock = schema.getOrCreateEdgeType(EdgeNames.ParentEdgeName)
      // edge constraints
      edgeBlock.getOrCreateProperty(EdgeNames.ParentEdgeName + "@out", Type.LINK, BlockVertex.vertexName)
      edgeBlock.getOrCreateProperty(EdgeNames.ParentEdgeName + "@in", Type.LINK, BlockVertex.vertexName)

      // transaction <-> block
      val edgeTxBlock = schema.getOrCreateEdgeType(EdgeNames.InBlockEdgeName)
      edgeTxBlock.getOrCreateProperty(EdgeNames.InBlockEdgeName + "@out", Type.LINK, TransactionVertex.vertexName)
      edgeTxBlock.getOrCreateProperty(EdgeNames.InBlockEdgeName + "@in", Type.LINK, BlockVertex.vertexName)

      // transaction -> Address Source
      val edgeTxAddressSource = schema.getOrCreateEdgeType(EdgeNames.hasSourceEdgeName)
      edgeTxAddressSource.getOrCreateProperty(EdgeNames.hasSourceEdgeName + "@out", Type.LINK, TransactionVertex.vertexName)
      edgeTxAddressSource.getOrCreateProperty(EdgeNames.hasSourceEdgeName + "@in", Type.LINK, AccountVertex.vertexName)

      // transaction -> Address Destination
      val edgeTxDestinationSource = schema.getOrCreateEdgeType(EdgeNames.hasDestinationEdgeName)
      edgeTxDestinationSource.getOrCreateProperty(EdgeNames.hasDestinationEdgeName + "@out", Type.LINK, TransactionVertex.vertexName)
      edgeTxDestinationSource.getOrCreateProperty(EdgeNames.hasDestinationEdgeName + "@in", Type.LINK, AccountVertex.vertexName)

      ()
    }.toEither
  }

  def make[F[_]: Sync: Logger](emptySchema: Schema): F[Either[Throwable, Unit]] =
    (for {
      _ <- EitherT(Sync[F].blocking(vertices.traverse(createVertex(_, emptySchema)))).map(_.void)
      _ <- EitherT(Sync[F].blocking(createEdges(emptySchema)))
      _ <- EitherT.liftF[F, Throwable, Unit](Logger[F].info("Schema created!"))
    } yield ()).value

}
