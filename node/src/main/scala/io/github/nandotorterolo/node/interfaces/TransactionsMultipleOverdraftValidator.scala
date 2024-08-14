package io.github.nandotorterolo.node.interfaces

import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.Transaction

/**
 * Overdraft - a valid signed transaction is rejected if the transaction amount exceeds the balance of the source wallet.
 * In this case, the validation is applied in a set of transaction being in the mempool
 */
trait TransactionsMultipleOverdraftValidator[F[_]] {
  def validate(transactions: Vector[Transaction]): F[Either[ModelThrowable, Unit]]

}
