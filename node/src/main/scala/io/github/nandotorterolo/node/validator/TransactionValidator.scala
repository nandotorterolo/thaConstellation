package io.github.nandotorterolo.node.validator

import cats.data.Chain
import cats.data.NonEmptyChain
import cats.data.Validated
import cats.data.ValidatedNec
import cats.effect.kernel.Async
import cats.implicits._
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.Transaction
import ModelThrowable.Message

/**
 * A transaction Validator with no external services needs
 * @tparam F
 */
trait TransactionValidator[F[_]] {

  def validate(t: Transaction): F[Either[ModelThrowable, Transaction]]

  def validateChain(t: Transaction): F[Either[NonEmptyChain[ModelThrowable], Transaction]]
}

object TransactionValidator {
  def build[F[_]: Async]: TransactionValidator[F] = new TransactionValidator[F] {
    override def validateChain(
        t: Transaction
    ): F[Either[NonEmptyChain[ModelThrowable], Transaction]] =
      validatorChain.foldMap(_.apply(t)).toEither.as(t).pure[F]

    override def validate(t: Transaction): F[Either[ModelThrowable, Transaction]] =
      validateChain(t).map(_.leftMap(errs => Message(errs.mkString_(","))))
  }

  private val validatorChain: Chain[Transaction => ValidatedNec[ModelThrowable, Unit]] =
    Chain(
      amountValidation,
      addressSourceDestinationValidation,
    )

  private def amountValidation(t: Transaction): ValidatedNec[ModelThrowable, Unit] =
    Validated.condNec(t.amount > 0, (), Message(s"Non Positive Amount(${t.amount})"))

  private def addressSourceDestinationValidation(
      t: Transaction
  ): ValidatedNec[ModelThrowable, Unit] = {
    Validated.condNec(
      t.source.value.compare(t.destination.value) != 0,
      (),
      Message("Source and Destination Address are the same")
    )
  }

}
