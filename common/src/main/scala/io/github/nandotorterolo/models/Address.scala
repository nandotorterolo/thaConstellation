package io.github.nandotorterolo.models

import java.security.KeyPair
import java.security.PublicKey

import cats.Show
import org.bouncycastle.jcajce.provider.digest.SHA3
import scodec.bits.ByteVector

/**
 * Right most 160 bits
 * @param value right most 160 bits from publicKey key
 * @return
 * @see https://github.com/hyperledger/web3j/blob/main/crypto/src/main/java/org/web3j/crypto/Keys.java
 */

/**
 * Address
 * Corresponds to the data associated with a public key
 * Should have some notion of an associated balance (which is updated by blocks
 *
 * @param value the value of the address, which is generated from the publick key
 * @param publicKey public key
 */
case class Address(addressId: AddressId, publicKey: ByteVector)

object Address {

  implicit val codec: scodec.Codec[Address] =
    (AddressId.codec ::        // 20 bytes
      scodec.codecs.bytes(170) // 170 bytes
    ).as[Address]

  implicit val showAddress: Show[Address] = Show.show(_.addressId.value.toBase58)

  def apply(publicKey: PublicKey): Address = {
    val encodedPublicKey = publicKey.getEncoded
    val value            = new SHA3.Digest512().digest(encodedPublicKey).takeRight(20)
    Address(AddressId(ByteVector(value)), publicKey = ByteVector(encodedPublicKey))
  }

  def apply(kp: KeyPair): Address = Address(kp.getPublic)
}
