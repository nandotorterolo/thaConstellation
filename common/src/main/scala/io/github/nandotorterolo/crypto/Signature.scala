package io.github.nandotorterolo.crypto

import scodec.bits.ByteVector

case class Signature(v: ByteVector)

object Signature {
  def apply(v: Array[Byte]): Signature = Signature(ByteVector(v))

  implicit val codec: scodec.Codec[Signature] = scodec.codecs.bytes(144).as[Signature]
}
