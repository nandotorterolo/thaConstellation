package cli.commands

import cats.effect.std.Console
import cats.effect.Async
import cats.implicits._
import cli._
import fs2.io.file.Path
import io.github.nandotorterolo.crypto.Cripto

class TransactionMenuCommand[F[_]: Async: Console](implicit cripto: Cripto[F], path: Path) {

  private val readCommand: CommandT[F, CliCommand] = {
    val res = for {
      _ <- write[F]("Transaction:")
      _ <- write[F]("Enter option. [create | byId]")

      r <- read[F]

    } yield r
    res.semiflatMap {
      case "create"     => CliCommand.CliTransactionCreate.some.widen[CliCommand].pure[F]
      case "byId"       => CliCommand.CliTransactionById.some.widen[CliCommand].pure[F]
      case "back" | "b" => CliCommand.CliMenu.some.widen[CliCommand].pure[F]
      case "help" | "h" => CliCommand.CliHelp.some.widen[CliCommand].pure[F]
      case "quit" | "q" => CliCommand.CliQuit.some.widen[CliCommand].pure[F]
      case other        => Console[F].println(s"Invalid option: $other").as(none[CliCommand])
    }.untilDefinedM
  }

  private def handleCommand(cliCommand: CliCommand): CommandT[F, Unit] =
    cliCommand match {
      case CliCommand.CliTransactionCreate => TransactionCreateCommand[F]
      case CliCommand.CliTransactionById   => TransactionByIdCommand[F]
      case CliCommand.CliMenu              => MenuCommand[F]
      case CliCommand.CliHelp              => HelpCommand[F](Command.menuTransaction)
      case CliCommand.CliQuit              => QuitCommand[F]
      case _                               => CommandT.commandTMonadThrow[F].raiseError[Unit](new IllegalStateException("handle account command miss match!"))
    }

  private val readHandleCommand: CommandT[F, Unit] = readCommand >>= handleCommand
}

object TransactionMenuCommand {
  def apply[F[_]: Async: Console](implicit cripto: Cripto[F], path: Path): CommandT[F, Unit] =
    new TransactionMenuCommand[F].readHandleCommand
}
