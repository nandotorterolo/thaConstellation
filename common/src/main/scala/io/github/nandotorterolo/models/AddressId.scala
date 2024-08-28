package io.github.nandotorterolo.models

import java.security.PublicKey

import cats.Show
import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signable
import io.github.nandotorterolo.crypto.Signature
import org.bouncycastle.jcajce.provider.digest.SHA3
import scodec.bits.BitVector
import scodec.bits.ByteVector
import scodec.Attempt
import scodec.DecodeResult

/**
 * Right most 160 bits
 * @param value right most 160 bits from publicKey key
 * @return
 * @see https://github.com/hyperledger/web3j/blob/main/crypto/src/main/java/org/web3j/crypto/Keys.java
 */

/**
 * AddressUnknown, is an address without carrying th the public key information
 * Corresponds to the data associated with a public key
 * Should have some notion of an associated balance (which is updated by blocks
 *
 * @param value the value of the address, which is generated from the public key
 */
case class AddressId(value: ByteVector) extends Signable[AddressId, AddressIdSigned] {
  override val encodeMe: AddressId = this

  override def build: (Hash, Signature, AddressId) => AddressIdSigned = AddressIdSigned(_, _, _)
}

object AddressId {

  implicit val showId: Show[AddressId] = Show.show(_.value.toBase58)

  implicit val codec: scodec.Codec[AddressId] = scodec.codecs.bytes(20).as[AddressId]

  /**
   * In different formats (PGP, SSH, X.509 certificates) key ID has different meaning.
   * Neither SSH nor X.509 have a "dedicated" concept of key ID, but some people use this term (including their software) -
   * in this case it's usually a hash of the public key or of the certificate in whole.
   *
   * "key identifier" extensions exist in X.509 certifiactes, and they sometimes are being referred to as key IDs.
   * Yet, this is not common - usually the hash (also sometimes called the fingerprint) is referenced as key ID.
   *
   * @param publicKey publik key
   * @return
   */
  def apply(publicKey: PublicKey): AddressId = {
    val encodedPublicKey = publicKey.getEncoded
    val value            = new SHA3.Digest512().digest(encodedPublicKey).takeRight(20)
    AddressId(ByteVector(value))
  }

  def apply(str: String): AddressId = {
    val b = ByteVector.fromValidBase58(str)
    require(b.size == 20)
    AddressId(b)
  }

  def fromValidB58(str: String): Attempt[DecodeResult[AddressId]] = codec.decode(BitVector.fromValidBase58(str))
  def fromValidB64(str: String): Attempt[DecodeResult[AddressId]] = codec.decode(BitVector.fromValidBase64(str))
}
