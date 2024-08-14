package io.github.nandotorterolo.node.interfaces

import io.github.nandotorterolo.models._

trait TransactionStorage[F[_]] {

  def get(transactionId: TransactionId): F[Either[ModelThrowable, Transaction]]

}
