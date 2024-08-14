package io.github.nandotorterolo.crypto

import scodec.bits.ByteVector

case class Hash(v: ByteVector)

object Hash {
  def apply(v: Array[Byte]): Hash = Hash(ByteVector(v))

  implicit val codec: scodec.Codec[Hash] = scodec.codecs.bytes(64).as[Hash]
}
