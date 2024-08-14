package io.github.nandotorterolo.node.interfaces

import io.github.nandotorterolo.models.Account
import io.github.nandotorterolo.models.AccountSigned
import io.github.nandotorterolo.models.AddressId

trait AccountsStorage[F[_]] {

  def insert(accountSigned: AccountSigned): F[Boolean]

  def contains(accountId: AddressId): F[Boolean]

  def get(accountId: AddressId): F[Option[Account]]

  def close(): F[Unit]

}
