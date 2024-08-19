package io.github.nandotorterolo.node.interfaces

import io.github.nandotorterolo.models.Account
import io.github.nandotorterolo.models.AccountSigned
import io.github.nandotorterolo.models.AddressId
import io.github.nandotorterolo.models.TransactionSigned

trait AccountsStorage[F[_]] {

  /**
   * Insert account
   * @param accountSigned account Signed
   * @return
   */
  def insert(accountSigned: AccountSigned): F[Boolean]

  /**
   * Contains account by id
   * @param id account id
   * @return
   */
  def contains(id: AddressId): F[Boolean]

  /**
   * Get account by Id
   * @param id account id
   * @return
   */
  def get(id: AddressId): F[Option[Account]]

  /**
   * Get all transaction where addressId was the source
   * @param id account id
   * @return
   */
  def getSourceTransactions(id: AddressId): F[Vector[TransactionSigned]]

  /**
   * Get all transaction where addressId was the destination
   * @param id account id
   * @return
   */
  def getDestinationTransactions(id: AddressId): F[Vector[TransactionSigned]]

}
