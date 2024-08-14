package cli

import cats._
import cats.implicits._

/**
 * A Command with a F[_] context
 */
final case class CommandT[F[_], A](value: F[Command[A]]) {

  def map[B](f: A => B)(implicit F: Functor[F]): CommandT[F, B] =
    CommandT(F.map(value)(_.map(f)))

  def flatMap[B](f: A => CommandT[F, B])(implicit F: Monad[F]): CommandT[F, B] =
    flatMapF(a => f(a).value)

  def flatMapF[B](f: A => F[Command[B]])(implicit F: Monad[F]): CommandT[F, B] =
    CommandT(
      F.flatMap(value) {
        case Command.Success(a) => f(a)
        case Command.Exit       => F.pure(Command.Exit)
        case Command.Menu       => F.pure(Command.Menu)
      }
    )

  def semiflatMap[B](f: A => F[B])(implicit F: Monad[F]): CommandT[F, B] =
    CommandT(
      F.flatMap(value) {
        case Command.Success(a) => f(a).map(Command.success)
        case Command.Exit       => F.pure(Command.Exit)
        case Command.Menu       => F.pure(Command.Menu)
      }
    )

  def subflatMap[B](f: A => Command[B])(implicit F: Monad[F]): CommandT[F, B] =
    CommandT(
      F.flatMap(value) {
        case Command.Success(a) => f(a).pure[F]
        case Command.Exit       => F.pure(Command.Exit)
        case Command.Menu       => F.pure(Command.Menu)
      }
    )
}

object CommandT {
  def liftF[F[_], A](fa: F[A])(implicit F: Functor[F]): CommandT[F, A] =
    CommandT(fa.map(Command.Success(_)))

  implicit def commandTMonadThrow[F[_]: MonadThrow]: MonadThrow[CommandT[F, *]] =
    new MonadThrow[CommandT[F, *]] {
      override def raiseError[A](e: Throwable): CommandT[F, A] =
        CommandT.liftF(MonadThrow[F].raiseError(e))

      override def handleErrorWith[A](fa: CommandT[F, A])(
          f: Throwable => CommandT[F, A]
      ): CommandT[F, A] = CommandT(
        fa.value.handleErrorWith(f(_).value)
      )

      override def pure[A](x: A): CommandT[F, A] = CommandT.liftF(x.pure[F])

      override def flatMap[A, B](fa: CommandT[F, A])(f: A => CommandT[F, B]): CommandT[F, B] =
        CommandT(
          Monad[F].flatMap(fa.value) {
            case Command.Success(a) => f(a).value
            case Command.Exit       => Monad[F].pure(Command.Exit)
            case Command.Menu       => Monad[F].pure(Command.Menu)
          }
        )

      override def tailRecM[A, B](a: A)(f: A => CommandT[F, Either[A, B]]): CommandT[F, B] =
        CommandT(
          f(a).value.flatMap {
            case Command.Menu              => Command.menu[B].pure[F]
            case Command.Exit              => Command.exit[B].pure[F]
            case Command.Success(Right(b)) => Command.success(b).pure[F]
            case Command.Success(Left(a))  => tailRecM(a)(f).value
          }
        )
    }
}
