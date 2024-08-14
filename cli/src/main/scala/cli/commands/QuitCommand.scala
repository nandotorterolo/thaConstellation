package cli.commands

import cats.effect.std.Console
import cats.effect.Sync
import cli._

class QuitCommand[F[_]: Sync: Console] {
  val command: CommandT[F, Unit] = write[F]("bye!").subflatMap(_ => Command.Exit)
}

object QuitCommand {
  def apply[F[_]: Sync: Console]: CommandT[F, Unit] = new QuitCommand[F].command
}
