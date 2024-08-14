package io.github.nandotorterolo.models

import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signature
import io.github.nandotorterolo.crypto.Signed

/**
 * @param hash:  Hash of the transaction should be what is being signed by the signature.
 * @param signature  Signature from the source signing the hash of all data within the transactionId
 *                    corresponding to the correct source address private key.
 * @param message:  The TransactionId
 */
case class TransactionIdSigned(hash: Hash, signature: Signature, message: TransactionId) extends Signed[TransactionId]

object TransactionIdSigned {
  implicit val codec: scodec.Codec[TransactionIdSigned] =
    (Hash.codec :: Signature.codec :: TransactionId.codec).as[TransactionIdSigned]

}
