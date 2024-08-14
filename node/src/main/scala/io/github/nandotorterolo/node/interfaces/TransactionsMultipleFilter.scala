package io.github.nandotorterolo.node.interfaces

import io.github.nandotorterolo.models.TransactionSigned

trait TransactionsMultipleFilter[F[_]] {
  def filterInvalids(transactions: Vector[TransactionSigned]): F[Vector[TransactionSigned]]
}
