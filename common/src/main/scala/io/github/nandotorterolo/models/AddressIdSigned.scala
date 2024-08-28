package io.github.nandotorterolo.models

import cats.Show
import io.circe.Encoder
import io.circe.Json
import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signature
import io.github.nandotorterolo.crypto.Signed
import scodec.bits.BitVector
import scodec.Attempt
import scodec.DecodeResult

case class AddressIdSigned(hash: Hash, signature: Signature, message: AddressId) extends Signed[AddressId]

object AddressIdSigned {

  implicit val showImpl: Show[AddressIdSigned] =
    Show.show(addressIdSigned => s"""
                                    |addressId: ${addressIdSigned.message.value.toBase58}""".stripMargin)

  implicit val codec: scodec.Codec[AddressIdSigned] =
    (Hash.codec :: Signature.codec :: AddressId.codec).as[AddressIdSigned]

  implicit val encoder: Encoder[AddressIdSigned] = new Encoder[AddressIdSigned] {
    override def apply(a: AddressIdSigned): Json = Json.obj(
      ("sign", Json.fromString(a.signature.value.toBase58)),
      ("addressId", Json.fromString(a.message.value.toBase58))
    )
  }

  implicit class RichAddressIdSigned(x: AddressIdSigned) {
    def encode: Attempt[BitVector] = codec.encode(x)

    def encodeToB58: Attempt[String] = encode.map(_.toBase58)
    def encodeToB64: Attempt[String] = encode.map(_.toBase64)

  }

  /**
   * Used to transfer a AddressIdSiged as String in Basic Credential Password
   * @param str
   * @return
   */
  def fromValidB58(str: String): Attempt[DecodeResult[AddressIdSigned]] = codec.decode(BitVector.fromValidBase58(str))
  def fromValidB64(str: String): Attempt[DecodeResult[AddressIdSigned]] = codec.decode(BitVector.fromValidBase64(str))

}
