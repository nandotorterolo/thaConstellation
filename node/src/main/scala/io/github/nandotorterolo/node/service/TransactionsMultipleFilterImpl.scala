package io.github.nandotorterolo.node.service

import cats.effect.Async
import cats.implicits._
import io.github.nandotorterolo.models.AddressId
import io.github.nandotorterolo.models.TransactionSigned
import io.github.nandotorterolo.node.interfaces._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

/**
 * TransactionsMultipleValidator
 * Validates if a sequence of transactions are valid, and return them.
 *
 * Example: Mempool contains 20 transactions.
 * 10 valids, 10 invalid
 * Output, 10 valid transactions
 */

class TransactionsMultipleFilterImpl[F[_]: Async](
    tmNonceV: TransactionsMultipleNonceValidator[F],
    tmOverdraftV: TransactionsMultipleOverdraftValidator[F]
) extends TransactionsMultipleFilter[F] {

  implicit val logger: Logger[F] = Slf4jLogger.getLogger[F]

  override def filterInvalids(transactions: Vector[TransactionSigned]): F[Vector[TransactionSigned]] = {

    val groupedTransactions: Map[AddressId, Vector[TransactionSigned]] = transactions.groupMap(t => t.message.source)(identity)

    groupedTransactions.foldLeft(Vector.empty[TransactionSigned].pure[F]) { (result, source_txs) =>
      val txsToValidate = source_txs._2.map(_.message)
      tmNonceV.validate(txsToValidate).flatMap {
        case Right(_) =>
          tmOverdraftV.validate(txsToValidate).flatMap {
            case Right(_) =>
              result.map(_ ++ source_txs._2)
            case Left(th) =>
              result.flatTap(_ => logger.warn(show"Invalid Multiple Overdraft Transactions $th"))
          }
        case Left(th) =>
          result.flatTap(_ => logger.warn(show"Invalid Multiple Nonce Transactions $th"))
      }

    }

  }
}

object TransactionsMultipleFilterImpl {
  def build[F[_]: Async](
      tmnv: TransactionsMultipleNonceValidator[F],
      tmov: TransactionsMultipleOverdraftValidator[F]
  ): TransactionsMultipleFilter[F] =
    new TransactionsMultipleFilterImpl[F](tmnv, tmov)
}
