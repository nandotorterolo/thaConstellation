package io.github.nandotorterolo.models

import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signature
import io.github.nandotorterolo.crypto.Signed

/**
 * @param hash:  Hash of the transaction should be what is being signed by the signature.
 * @param signature  Signature from the source signing the hash of all data within the transactionId
 *                    corresponding to the correct source address private key.
 * @param message:  The TransactionIdAddressId
 */
case class TransactionIdAddressIdSigned(hash: Hash, signature: Signature, message: TransactionIdAddressId) extends Signed[TransactionIdAddressId]

object TransactionIdAddressIdSigned {
  implicit val codec: scodec.Codec[TransactionIdAddressIdSigned] =
    (Hash.codec :: Signature.codec :: TransactionIdAddressId.codec).as[TransactionIdAddressIdSigned]

}
