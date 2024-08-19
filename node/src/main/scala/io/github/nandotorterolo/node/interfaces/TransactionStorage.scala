package io.github.nandotorterolo.node.interfaces

import io.github.nandotorterolo.models._

trait TransactionStorage[F[_]] {

  /**
   * Get transaction by id
   * @param transactionId transaction id
   * @return
   */
  def get(transactionId: TransactionId): F[Either[ModelThrowable, Transaction]]

}
