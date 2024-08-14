package io.github.nandotorterolo.models

import cats.Show
import io.circe.Encoder
import io.circe.Json
import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signable
import io.github.nandotorterolo.crypto.Signature
import scodec.codecs._

/**
 * Account
 * @param address  address
 * @param balance  balance
 * @param latestUsedNonce
 */
case class Account(address: Address, balance: Double, latestUsedNonce: Int) extends Signable[Account, AccountSigned] {

  override val encodeMe: Account                                  = this
  override def build: (Hash, Signature, Account) => AccountSigned = AccountSigned(_, _, _)
}

object Account {

  implicit val showAccount: Show[Account] =
    Show.show(a => s"""
                      |address: ${a.address.addressId.value.toBase58}
                      |balance: ${a.balance}""".stripMargin)

  implicit val encodeAccount: Encoder[Account] = new Encoder[Account] {
    override def apply(a: Account): Json = Json.obj(
      ("address", Json.fromString(a.address.addressId.value.toBase58)),
//      ("publicKey", Json.fromString(a.address.publicKey.toBase58)),
      ("balance", Json.fromDoubleOrNull(a.balance)),
      ("latestUsedNonce", Json.fromInt(a.latestUsedNonce))
    )
  }

  implicit val codec: scodec.Codec[Account] =
    (Address.codec :: double :: int32).as[Account]

}
