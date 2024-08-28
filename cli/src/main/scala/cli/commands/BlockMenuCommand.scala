package cli.commands

import cats.effect.std.Console
import cats.effect.Async
import cats.implicits._
import cli._
import fs2.io.file.Path
import io.github.nandotorterolo.crypto.Cripto

class BlockMenuCommand[F[_]: Async: Console](implicit cripto: Cripto[F], path: Path) {

  private val readCommand: CommandT[F, CliCommand] = {
    val res = for {
      _ <- write[F]("Block:")
      _ <- write[F]("Enter option. [byId | byIdV2 | bySN]")

      r <- read[F]

    } yield r
    res.semiflatMap {
      case "byId"                 => CliCommand.CliBlockById.some.widen[CliCommand].pure[F]
      case "byIdV2"               => CliCommand.CliBlockByIdV2.some.widen[CliCommand].pure[F]
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
      case CliCommand.CliBlockById        => BlockByIdBasicAuthCommand[F]
      case CliCommand.CliBlockByIdV2      => BlockByIdV2MessageSignaturesCommand[F]
      case CliCommand.CliBlockBySeqNumber => BlockBySeqNumberCommand[F]
      case CliCommand.CliHelp             => HelpCommand[F](Command.menuBlock)
      case CliCommand.CliQuit             => QuitCommand[F]
      case _                              => CommandT.commandTMonadThrow[F].raiseError[Unit](new IllegalStateException("handle block command miss match!"))
    }

  private val readHandleCommand: CommandT[F, Unit] = readCommand >>= handleCommand
}

object BlockMenuCommand {
  def apply[F[_]: Async: Console](implicit cripto: Cripto[F], path: Path): CommandT[F, Unit] =
    new BlockMenuCommand[F].readHandleCommand
}
