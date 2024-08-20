package cli.commands

import cats.effect.std.Console
import cats.effect.Sync
import cli._

class MenuCommand[F[_]: Sync: Console] {
  val command: CommandT[F, Unit] = write[F]("CLI:").subflatMap(_ => Command.Menu)
}

object MenuCommand {
  def apply[F[_]: Sync: Console]: CommandT[F, Unit] = new MenuCommand[F].command
}
