package cli.commands

import cats.effect.std.Console
import cats.effect.Async
import cats.implicits._
import cli._
import fs2.io.file.Path
import io.github.nandotorterolo.crypto.Cripto

class AccountMenuCommand[F[_]: Async: Console](implicit cripto: Cripto[F], path: Path) {

  private val readCommand: CommandT[F, CliCommand] = {
    val res = for {
      _ <- write[F]("Account:")
      _ <- write[F]("Enter option. [gen | reg | inspect | bal | txs]")

      r <- read[F]

    } yield r
    res.semiflatMap {
      case "gen" | "generateKeyPair" => CliCommand.CliAccountGenerateKP.some.widen[CliCommand].pure[F]
      case "reg" | "registration"    => CliCommand.CliAccountRegistration.some.widen[CliCommand].pure[F]
      case "inspect"                 => CliCommand.CliAccountInspect.some.widen[CliCommand].pure[F]
      case "bal" | "balance"         => CliCommand.CliAccountBalance.some.widen[CliCommand].pure[F]
      case "txs" | "transactions"    => CliCommand.CliAccountTransactions.some.widen[CliCommand].pure[F]
      case "back" | "b"              => CliCommand.CliMenu.some.widen[CliCommand].pure[F]
      case "help" | "h"              => CliCommand.CliHelp.some.widen[CliCommand].pure[F]
      case "quit" | "exit" | "q"     => CliCommand.CliQuit.some.widen[CliCommand].pure[F]
      case other                     => Console[F].println(s"Invalid option: $other").as(none[CliCommand])
    }.untilDefinedM
  }

  private def handleCommand(cliCommand: CliCommand): CommandT[F, Unit] =
    cliCommand match {
      case CliCommand.CliAccountGenerateKP   => AccountGenerateCommand[F]
      case CliCommand.CliAccountRegistration => AccountRegistrationCommand[F]
      case CliCommand.CliAccountInspect      => AccountInspectCommand[F]
      case CliCommand.CliAccountBalance      => AccountBalanceCommand[F]
      case CliCommand.CliAccountTransactions => AccountTransactionsCommand[F]
      case CliCommand.CliMenu                => MenuCommand[F]
      case CliCommand.CliHelp                => HelpCommand[F](Command.menuAccount)
      case CliCommand.CliQuit                => QuitCommand[F]
      case _                                 => CommandT.commandTMonadThrow[F].raiseError[Unit](new IllegalStateException("handle account command miss match!"))
    }

  private val readHandleCommand: CommandT[F, Unit] = readCommand >>= handleCommand
}

object AccountMenuCommand {
  def apply[F[_]: Async: Console](implicit cripto: Cripto[F], path: Path): CommandT[F, Unit] =
    new AccountMenuCommand[F].readHandleCommand
}
