package io.github.nandotorterolo.models

import cats.Show
import scodec.bits.ByteVector

case class BlockId(value: ByteVector)

object BlockId {

  implicit val showId: Show[BlockId] = Show.show(_.value.toBase58)

  implicit val codec: scodec.Codec[BlockId] = scodec.codecs.bytes(64).as[BlockId]

}
