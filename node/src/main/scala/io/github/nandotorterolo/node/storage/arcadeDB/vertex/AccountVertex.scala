package io.github.nandotorterolo.node.storage.arcadeDB.vertex

import com.arcadedb.database.Database
import com.arcadedb.graph.MutableVertex
import com.arcadedb.graph.Vertex
import com.arcadedb.schema.Schema.INDEX_TYPE
import com.arcadedb.schema.Type
import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signature
import io.github.nandotorterolo.models.Account
import io.github.nandotorterolo.models.AccountSigned
import io.github.nandotorterolo.models.Address
import io.github.nandotorterolo.models.AddressId
import io.github.nandotorterolo.node.storage.arcadeDB.dsl._
import io.github.nandotorterolo.node.storage.arcadeDB.interfaces.LookUps
import scodec.bits.ByteVector

object AccountVertex extends CodecVertex[AccountSigned] with LookUps[AccountSigned] {

  override val vertexName: String = "Account"
  override val keyName: String    = Fields.Key

  object Fields {
    val Key             = "key"
    val Balance         = "balance"
    val LatestUsedNonce = "latestUsedNonce"
    val Address         = "address"
    val PublicKey       = "publicKey"
    val Hash            = "Hash"
    val Signature       = "signature"
  }

  val make: VertexDsl[Account] = VertexDsl.create(
    vertexName,
    VertexFactory[Account]
      .withProperty(VertexProperty(Fields.Key, Type.STRING))
      .withProperty(VertexProperty(Fields.Balance, Type.DOUBLE))
      .withProperty(VertexProperty(Fields.LatestUsedNonce, Type.INTEGER))
      .withProperty(VertexProperty(Fields.Address, Type.BINARY))
      .withProperty(VertexProperty(Fields.PublicKey, Type.BINARY))
      .withProperty(VertexProperty(Fields.Hash, Type.BINARY))
      .withProperty(VertexProperty(Fields.Signature, Type.BINARY))
      .withIndex(VertexIndex(Fields.Key, INDEX_TYPE.LSM_TREE, unique = true))
  )

  def encode(signed: AccountSigned)(db: Database): MutableVertex = {
    db.newVertex(vertexName)
      .set(Fields.Key, signed.message.address.addressId.value.toBase58) // the key used is not the hash
      .set(Fields.Balance, signed.message.balance)
      .set(Fields.LatestUsedNonce, signed.message.latestUsedNonce)
      .set(Fields.Address, signed.message.address.addressId.value.toArray)
      .set(Fields.PublicKey, signed.message.address.publicKey.toArray)
      .set(Fields.Hash, signed.hash.value.toArray)
      .set(Fields.Signature, signed.signature.value.toArray)
  }

  def decode(v: Vertex): AccountSigned = {
    AccountSigned(
      hash = Hash(ByteVector(v.getBinary(Fields.Hash))),
      signature = Signature(ByteVector(v.getBinary(Fields.Signature))),
      message = Account(
        address = Address(
          AddressId(ByteVector(v.getBinary(Fields.Address))),
          ByteVector(v.getBinary(Fields.PublicKey))
        ),
        balance = v.getDouble(Fields.Balance),
        latestUsedNonce = v.getInteger(Fields.LatestUsedNonce)
      )
    )
  }
}
