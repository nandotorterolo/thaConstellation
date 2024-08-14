package io.github.nandotorterolo.node.interfaces

import io.github.nandotorterolo.models.TransactionSigned

trait MemPoolService[F[_]] {

  /**
   * add a new transaction to the mempool
   * @param t transaction
   * @return
   */
  def addTransaction(t: TransactionSigned): F[Unit]

  /**
   * given a list of transaction from the mempool, run validators, discard invalid transactions, block promotion
   * @param txs transactions
   * @return
   */
  def processListTxs(txs: Vector[TransactionSigned]): F[Unit]
}
