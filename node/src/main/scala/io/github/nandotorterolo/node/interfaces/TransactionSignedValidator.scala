package io.github.nandotorterolo.node.interfaces

import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.TransactionSigned

/**
 * A transaction validator with external services, in this case, a StorageService
 */
trait TransactionSignedValidator[F[_]] {

  /**
   * Transaction signed by wrong wallet - a signed transaction signed by a different
   * wallet than the source wallet is rejected and does not result in a balance transfer.
   */
  def validate(transactionSigned: TransactionSigned): F[Either[ModelThrowable, Unit]]

}
