package io.github.nandotorterolo.models

import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signable
import io.github.nandotorterolo.crypto.Signature

case class TransactionIdAddressId(transactionId: TransactionId, addressId: AddressId) extends Signable[TransactionIdAddressId, TransactionIdAddressIdSigned] {
  override val encodeMe: TransactionIdAddressId = this

  override def build: (Hash, Signature, TransactionIdAddressId) => TransactionIdAddressIdSigned = TransactionIdAddressIdSigned(_, _, _)
}

object TransactionIdAddressId {

  implicit val codec: scodec.Codec[TransactionIdAddressId] =
    (TransactionId.codec :: AddressId.codec).as[TransactionIdAddressId]

}
