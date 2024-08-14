package io.github.nandotorterolo.node.interfaces

import io.github.nandotorterolo.models.AddressId
import io.github.nandotorterolo.models.Transaction

trait AccountsUpdater {

  /**
   * Given transaction, return the operatations that accounts balances should add or subtract when a block is accepted
   * @param transactions transactions
   * @return Address and balances to update on Accounts
   */
  def balances(transactions: Vector[Transaction]): Map[AddressId, Double]

  /**
   * Given transaction, return the operatations that accounts nonces should updated when a block is accepted
   * @param transactions transactions
   * @return Address and nonces to update on Accounts
   */
  def nonces(transactions: Vector[Transaction]): Map[AddressId, Int]

}
