package io.github.nandotorterolo.models

import cats.Show
import io.circe.Encoder
import io.circe.Json
import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signature
import io.github.nandotorterolo.crypto.Signed
import scodec.bits.BitVector
import scodec.Attempt

case class AddressIdSigned(hash: Hash, signature: Signature, message: AddressId) extends Signed[AddressId]

object AddressIdSigned {

  implicit val showImpl: Show[AddressIdSigned] =
    Show.show(addressIdSigned => s"""
                                    |addressId: ${addressIdSigned.message.value.toBase58}""".stripMargin)

  implicit val codec: scodec.Codec[AddressIdSigned] =
    (Hash.codec :: Signature.codec :: AddressId.codec).as[AddressIdSigned]

  implicit val encoder: Encoder[AddressIdSigned] = new Encoder[AddressIdSigned] {
    override def apply(a: AddressIdSigned): Json = Json.obj(
      ("sign", Json.fromString(a.signature.v.toBase58)),
      ("addressId", Json.fromString(a.message.value.toBase58))
    )
  }

  implicit class RichAddressIdSigned(x: AddressIdSigned) {
    def encode: Attempt[BitVector] = codec.encode(x)
  }

}
