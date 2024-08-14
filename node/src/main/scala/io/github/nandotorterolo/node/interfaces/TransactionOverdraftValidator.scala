package io.github.nandotorterolo.node.interfaces

import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.Transaction

/**
 * A transaction validator with external services, in this case, a StorageService
 */
trait TransactionOverdraftValidator[F[_]] {

  /**
   * Overdraft - a valid signed transaction is rejected if the transaction amount exceeds the balance of the source wallet.
   */
  def validate(transaction: Transaction): F[Either[ModelThrowable, Unit]]

}
