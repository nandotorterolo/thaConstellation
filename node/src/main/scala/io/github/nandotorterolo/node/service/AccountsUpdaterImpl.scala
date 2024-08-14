package io.github.nandotorterolo.node.service

import cats.implicits._
import io.github.nandotorterolo.models.AddressId
import io.github.nandotorterolo.models.Transaction
import io.github.nandotorterolo.node.interfaces.AccountsUpdater

object AccountsUpdaterImpl extends AccountsUpdater {

  override def balances(transactions: Vector[Transaction]): Map[AddressId, Double] = {

    val amountsToSubstract: Map[AddressId, Double] = transactions
      .map(t => t.source -> t)
      .groupMapReduce(_._1)(_._2.amount)(_ + _)
      .map(addressId_ammout => addressId_ammout._1 -> addressId_ammout._2 * -1)

    val amountsToAdd: Map[AddressId, Double] = transactions
      .map(t => t.destination -> t)
      .groupMapReduce(_._1)(_._2.amount)(_ + _)

    amountsToAdd.alignMergeWith(amountsToSubstract)(_ + _)

  }

  override def nonces(transactions: Vector[Transaction]): Map[AddressId, Int] = {
    transactions
      .map(t => t.source -> t)
      .groupMap(_._1)(_._2.nonce)
      .map(addressId_nonces => addressId_nonces._1 -> addressId_nonces._2.max)
  }

}
