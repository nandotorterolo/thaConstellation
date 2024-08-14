package io.github.nandotorterolo.node.interfaces

import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.Transaction

/**
 * A transaction validator with external services
 */
trait TransactionNonceValidator[F[_]] {

  /**
   * Repeating transaction - a valid signed transaction is rejected if it repeats a nonce that’s already been accepted into a block.
   */
  def validate(transaction: Transaction): F[Either[ModelThrowable, Unit]]

}
