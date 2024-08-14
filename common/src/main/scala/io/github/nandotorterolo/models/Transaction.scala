package io.github.nandotorterolo.models

import io.circe.Encoder
import io.circe.Json
import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signable
import io.github.nandotorterolo.crypto.Signature

/**
 * @param source  Source, destination addresses for moving funds
 * @param destination Source, destination addresses for moving funds
 * @param amount Amount being transferred
 * @param signature  Signature from the source signing the hash of all data within the transaction
 *                    corresponding to the correct source address private key.
 *                    hash:  Hash of the transaction should be what is being signed by the signature.
 * @param nonce:  Nonce or ordinal associated with the number of sequential transactions used for
 *                    the source address, monotonically increasing and used to prevent duplicate
 *                    replay transactions.
 */
case class Transaction(source: AddressId, destination: AddressId, amount: Double, nonce: Int) extends Signable[Transaction, TransactionSigned] {
  override val encodeMe: Transaction = this

  override def build: (Hash, Signature, Transaction) => TransactionSigned = TransactionSigned(_, _, _)
}

object Transaction {
  implicit val codec: scodec.Codec[Transaction] =
    (
      AddressId.codec ::
        AddressId.codec ::
        scodec.codecs.double ::
        scodec.codecs.int32
    ).as[Transaction]

  implicit val encoder: Encoder[Transaction] = new Encoder[Transaction] {
    override def apply(t: Transaction): Json = Json.obj(
      ("source", Json.fromString(t.source.value.toBase58)),
      ("destination", Json.fromString(t.destination.value.toBase58)),
      ("amount", Json.fromDoubleOrNull(t.amount)),
      ("nonce", Json.fromInt(t.nonce))
    )
  }

}
