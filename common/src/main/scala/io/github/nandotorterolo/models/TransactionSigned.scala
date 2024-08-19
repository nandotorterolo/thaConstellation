package io.github.nandotorterolo.models

import io.circe.syntax.EncoderOps
import io.circe.Encoder
import io.circe.Json
import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signature
import io.github.nandotorterolo.crypto.Signed

/**
 * @param hash:  Hash of the transaction should be what is being signed by the signature.
 * @param signature  Signature from the source signing the hash of all data within the transaction
 *                    corresponding to the correct source address private key.
 * @param message:  The Transaction
 */
case class TransactionSigned(hash: Hash, signature: Signature, message: Transaction) extends Signed[Transaction]

object TransactionSigned {
  implicit val codec: scodec.Codec[TransactionSigned] =
    (Hash.codec :: Signature.codec :: Transaction.codec).as[TransactionSigned]

  implicit val encoder: Encoder[TransactionSigned] = new Encoder[TransactionSigned] {
    override def apply(b: TransactionSigned): Json = Json.obj(
      ("hash", Json.fromString(b.hash.value.toBase58)),
//      ("sign", Json.fromString(b.signature.v.toBase58)),
      ("transaction", b.message.asJson)
    )
  }

}
