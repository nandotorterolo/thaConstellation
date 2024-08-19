package io.github.nandotorterolo.node.storage.arcadeDB.vertex

import com.arcadedb.database.Database
import com.arcadedb.graph.MutableVertex
import com.arcadedb.graph.Vertex
import com.arcadedb.schema.Schema.INDEX_TYPE
import com.arcadedb.schema.Type
import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signature
import io.github.nandotorterolo.models.Block
import io.github.nandotorterolo.models.BlockId
import io.github.nandotorterolo.models.BlockSigned
import io.github.nandotorterolo.models.TransactionId
import io.github.nandotorterolo.node.storage.arcadeDB.dsl._
import io.github.nandotorterolo.node.storage.arcadeDB.interfaces.LookUps
import scodec.bits.ByteVector

object BlockVertex extends CodecVertex[BlockSigned] with LookUps[BlockSigned] {

  override val vertexName: String = "Block"
  override val keyName: String    = Fields.Key

  object Fields {
    val Key            = "key"
    val SequenceNumber = "sequenceNumber"
    val PriorBlock     = "priorBlock"
    val Transactions   = "transactions"
    val Hash           = "Hash"
    val Signature      = "signature"
  }

  val make: VertexDsl[Block] = VertexDsl.create(
    vertexName,
    VertexFactory[Block]
      .withProperty(VertexProperty(Fields.Key, Type.STRING))
      .withProperty(VertexProperty(Fields.SequenceNumber, Type.INTEGER))
      .withProperty(VertexProperty(Fields.PriorBlock, Type.BINARY))
      .withProperty(VertexProperty(Fields.Transactions, Type.STRING))
      .withProperty(VertexProperty(Fields.Hash, Type.BINARY))
      .withProperty(VertexProperty(Fields.Signature, Type.BINARY))
      .withIndex(VertexIndex(Fields.Key, INDEX_TYPE.LSM_TREE, unique = true))
  )

  def encode(signed: BlockSigned)(db: Database): MutableVertex = {
    db.newVertex(vertexName)
      .set(Fields.Key, signed.hash.value.toBase58)
      .set(Fields.SequenceNumber, signed.message.sequenceNumber)
      .set(Fields.PriorBlock, signed.message.priorBlock.value.toArray)
      .set(Fields.Transactions, signed.message.transactions.map(_.value.toBase58).mkString(","))
      .set(Fields.Hash, signed.hash.value.toArray)
      .set(Fields.Signature, signed.signature.value.toArray)
  }

  def decode(v: Vertex): BlockSigned = {
    BlockSigned(
      hash = Hash(ByteVector(v.getBinary(Fields.Hash))),
      signature = Signature(ByteVector(v.getBinary(Fields.Signature))),
      Block(
        priorBlock = BlockId(ByteVector(v.getBinary(Fields.PriorBlock))),
        sequenceNumber = v.getInteger(Fields.SequenceNumber),
        transactions = v
          .getString(Fields.Transactions)
          .split(",")
          .map(ByteVector.fromValidBase58(_))
          .map(TransactionId(_))
          .toVector
      )
    )
  }

}
