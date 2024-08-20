package cli.commands

import cats.effect.std.Console
import cats.effect.Async
import cats.implicits._
import cli._

class BlockMenuCommand[F[_]: Async: Console] {

  private val readCommand: CommandT[F, CliCommand] = {
    val res = for {
      _ <- write[F]("Block:")
      _ <- write[F]("Enter option. [byId | bySN]")

      r <- read[F]

    } yield r
    res.semiflatMap {
      case "byId"                 => CliCommand.CliBlockById.some.widen[CliCommand].pure[F]
      case "bySN" | "bySeqNumber" => CliCommand.CliBlockBySeqNumber.some.widen[CliCommand].pure[F]
      case "back" | "b"           => CliCommand.CliMenu.some.widen[CliCommand].pure[F]
      case "help" | "h"           => CliCommand.CliHelp.some.widen[CliCommand].pure[F]
      case "quit" | "exit" | "q"  => CliCommand.CliQuit.some.widen[CliCommand].pure[F]
      case other                  => Console[F].println(s"Invalid option: $other").as(none[CliCommand])
    }.untilDefinedM
  }

  private def handleCommand(cliCommand: CliCommand): CommandT[F, Unit] =
    cliCommand match {
      case CliCommand.CliMenu             => MenuCommand[F]
      case CliCommand.CliBlockById        => BlockByIdCommand[F]
      case CliCommand.CliBlockBySeqNumber => BlockBySeqNumberCommand[F]
      case CliCommand.CliHelp             => HelpCommand[F](Command.menuBlock)
      case CliCommand.CliQuit             => QuitCommand[F]
      case _                              => CommandT.commandTMonadThrow[F].raiseError[Unit](new IllegalStateException("handle block command miss match!"))
    }

  private val readHandleCommand: CommandT[F, Unit] = readCommand >>= handleCommand
}

object BlockMenuCommand {
  def apply[F[_]: Async: Console]: CommandT[F, Unit] =
    new BlockMenuCommand[F].readHandleCommand
}
