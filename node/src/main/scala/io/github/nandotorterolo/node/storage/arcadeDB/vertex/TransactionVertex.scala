package io.github.nandotorterolo.node.storage.arcadeDB.vertex

import com.arcadedb.database.Database
import com.arcadedb.graph.MutableVertex
import com.arcadedb.graph.Vertex
import com.arcadedb.schema.Schema.INDEX_TYPE
import com.arcadedb.schema.Type
import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signature
import io.github.nandotorterolo.models.AddressId
import io.github.nandotorterolo.models.Transaction
import io.github.nandotorterolo.models.TransactionSigned
import io.github.nandotorterolo.node.storage.arcadeDB.dsl._
import io.github.nandotorterolo.node.storage.arcadeDB.interfaces.LookUps
import scodec.bits.ByteVector

object TransactionVertex extends CodecVertex[TransactionSigned] with LookUps[TransactionSigned] {

  override val vertexName: String = "Transaction"
  override val keyName: String    = Fields.Key

  object Fields {

    val Key         = "key"
    val Source      = "source"
    val Destination = "destination"
    val Amount      = "amount"
    val nonce       = "nonce"
    val Hash        = "Hash"
    val Signature   = "signature"
  }

  val make: VertexDsl[Transaction] = VertexDsl.create(
    vertexName,
    VertexFactory[Transaction]
      .withProperty(VertexProperty(Fields.Key, Type.STRING))
      .withProperty(VertexProperty(Fields.Source, Type.STRING))
      .withProperty(VertexProperty(Fields.Destination, Type.STRING))
      .withProperty(VertexProperty(Fields.Amount, Type.DOUBLE))
      .withProperty(VertexProperty(Fields.nonce, Type.INTEGER))
      .withProperty(VertexProperty(Fields.Hash, Type.BINARY))
      .withProperty(VertexProperty(Fields.Signature, Type.BINARY))
      .withIndex(VertexIndex(Fields.Key, INDEX_TYPE.LSM_TREE, unique = true))
  )

  def encode(signed: TransactionSigned)(db: Database): MutableVertex = {
    db.newVertex(vertexName)
      .set(Fields.Key, signed.hash.value.toBase58)
      .set(Fields.Source, signed.message.source.value.toBase58)
      .set(Fields.Destination, signed.message.destination.value.toBase58)
      .set(Fields.Amount, signed.message.amount)
      .set(Fields.nonce, signed.message.nonce)
      .set(Fields.Hash, signed.hash.value.toArray)
      .set(Fields.Signature, signed.signature.value.toArray)
  }

  def decode(v: Vertex): TransactionSigned = {
    TransactionSigned(
      hash = Hash(ByteVector(v.getBinary(Fields.Hash))),
      signature = Signature(ByteVector(v.getBinary(Fields.Signature))),
      Transaction(
        source = AddressId(ByteVector.fromValidBase58(v.getString(Fields.Source))),
        destination = AddressId(ByteVector.fromValidBase58(v.getString(Fields.Destination))),
        amount = v.getDouble(Fields.Amount),
        nonce = v.getInteger(Fields.nonce)
      )
    )
  }

}
