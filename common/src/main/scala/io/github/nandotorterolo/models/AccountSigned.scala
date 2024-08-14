package io.github.nandotorterolo.models

import cats.Show
import io.circe.syntax.EncoderOps
import io.circe.Encoder
import io.circe.Json
import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signature
import io.github.nandotorterolo.crypto.Signed
import scodec.bits.BitVector
import scodec.Attempt

/**
 * @param hash:  Hash of the Account should be what is being signed by the signature.
 * @param signature  Signature from the source signing the hash of all data within the account
 *                    corresponding to the correct source address private key.
 * @param message:  The Account
 */
case class AccountSigned(hash: Hash, signature: Signature, message: Account) extends Signed[Account]

object AccountSigned {

  implicit val showImpl: Show[AccountSigned] =
    Show.show(as => s"""accountId: ${as.message.address.addressId.value.toBase58}""".stripMargin)

  implicit val codec: scodec.Codec[AccountSigned] =
    (Hash.codec :: Signature.codec :: Account.codec).as[AccountSigned]

  implicit val encoder: Encoder[AccountSigned] = new Encoder[AccountSigned] {
    override def apply(a: AccountSigned): Json = Json.obj(
//      ("sign", Json.fromString(a.signature.v.toBase58)),
      ("account", a.message.asJson)
    )
  }

  implicit class RichAccountSigned(x: AccountSigned) {
    def encode: Attempt[BitVector] = codec.encode(x)
  }

}
