package io.github.nandotorterolo.models

import cats.Show
import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signable
import io.github.nandotorterolo.crypto.Signature
import scodec.bits.ByteVector

case class TransactionId(value: ByteVector) extends Signable[TransactionId, TransactionIdSigned] {
  override val encodeMe: TransactionId = this

  override def build: (Hash, Signature, TransactionId) => TransactionIdSigned = TransactionIdSigned(_, _, _)
}

object TransactionId {

  implicit val showId: Show[TransactionId] = Show.show(_.value.toBase58)

  implicit val codec: scodec.Codec[TransactionId] = scodec.codecs.bytes(64).as[TransactionId]

}
