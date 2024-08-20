package cli

import scala.annotation.tailrec

import cats._

sealed abstract class Command[+T]

object Command {

  case object Exit extends Command[Nothing]

  def exit[T]: Command[T] = Exit

  case object Menu extends Command[Nothing]

  def menu[T]: Command[T] = Menu

  case object MenuBlock extends Command[Nothing]

  def menuBlock[T]: Command[T] = MenuBlock

  case object MenuTransaction extends Command[Nothing]

  def menuTransaction[T]: Command[T] = MenuTransaction

  case object MenuAccount extends Command[Nothing]

  def menuAccount[T]: Command[T] = MenuAccount

  case class Success[+T](a: T) extends Command[T]

  def success[T](t: T): Command[T] = Success(t)

  implicit val commandStageMonad: Monad[Command] =
    new Monad[Command] {
      override def pure[A](x: A): Command[A] = Success(x)

      override def flatMap[A, B](fa: Command[A])(f: A => Command[B]): Command[B] =
        fa match {
          case Exit            => Exit
          case Menu            => Menu
          case MenuBlock       => MenuBlock
          case MenuAccount     => MenuAccount
          case MenuTransaction => MenuTransaction
          case Success(a)      => f(a)
        }

      @tailrec
      override def tailRecM[A, B](a: A)(f: A => Command[Either[A, B]]): Command[B] =
        f(a) match {
          case Exit              => Exit
          case Menu              => Menu
          case MenuBlock         => MenuBlock
          case MenuAccount       => MenuAccount
          case MenuTransaction   => MenuTransaction
          case Success(Left(l))  => tailRecM(l)(f)
          case Success(Right(r)) => Success(r)
        }
    }
}
