package cli.commands

import cats.effect.std.Console
import cats.effect.Sync
import cli._

class HelpCommand[F[_]: Sync: Console] {
  val command: CommandT[F, Unit] =
    write[F]("""
               |- generateKeyPair: generates key private public keys (alias: gen)
               |- inspect: provide information of publick key
               |- reg: register key on chain
               |- tx: create a transaction
               |- help: show help
               |- quit: exit cli
               |""".stripMargin).subflatMap(_ => Command.Menu)
}

object HelpCommand {
  def apply[F[_]: Sync: Console]: CommandT[F, Unit] = new HelpCommand[F].command
}
