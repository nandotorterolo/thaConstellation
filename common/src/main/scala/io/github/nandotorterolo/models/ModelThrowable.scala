package io.github.nandotorterolo.models

import cats.implicits.showInterpolator
import cats.Show

/**
 * Your Throwables Errors
 */
sealed abstract class ModelThrowable extends Throwable

object ModelThrowable {
  case class Message(msg: String)         extends ModelThrowable
  case class ConflictMessage(msg: String) extends ModelThrowable
  case object InvalidRequestParam         extends ModelThrowable
  case object EntityNotFound              extends ModelThrowable
  case object SignatureValidation         extends ModelThrowable
  case object Unauthorized                extends ModelThrowable

  implicit val showValidationError: Show[ModelThrowable] = {
    case Message(s)          => show"$s"
    case ConflictMessage(s)  => show"$s"
    case InvalidRequestParam => show"Invalid Request Parameters"
    case EntityNotFound      => show"Entity not found"
    case SignatureValidation => show"Signature Validation error"
    case Unauthorized        => show"Unauthorized"
    case _                   => show"missing show implementation! fix it"
  }
}
