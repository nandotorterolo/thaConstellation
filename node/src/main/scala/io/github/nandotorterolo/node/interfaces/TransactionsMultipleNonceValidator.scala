package io.github.nandotorterolo.node.interfaces

import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.Transaction

/**
 * Repeating transaction - a valid signed transaction is rejected if it repeats a nonce that’s already been accepted into a block.
 * In this case, the validation is applied in a set of transaction being in the mempool
 */
trait TransactionsMultipleNonceValidator[F[_]] {
  def validate(transactions: Vector[Transaction]): F[Either[ModelThrowable, Unit]]

}
