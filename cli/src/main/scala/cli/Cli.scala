package cli

import java.security.Security

import cats.effect.kernel.Sync
import cats.effect.std.Console
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.implicits._
import cli.commands._
import cli.Command._
import com.typesafe.config.ConfigFactory
import fs2.io.file.Path
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import org.bouncycastle.jce.provider.BouncyCastleProvider

object Cli extends IOApp.Simple {

  private implicit val c: Console[IO]     = Console.make[IO]
  private implicit val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]
  private implicit val pathConfig: Path   = Path(ConfigFactory.load().getString("cli.path"))

  private val readCommand: CommandT[IO, CliCommand] = {
    val res = for {
      _ <- write[IO]("Enter option. [account | transaction | block | help | quit]")
      r <- read[IO]
    } yield r
    res.semiflatMap {
      case "account"             => CliCommand.CliAccountMenu.some.widen[CliCommand].pure[IO]
      case "transaction"         => CliCommand.CliTransactionMenu.some.widen[CliCommand].pure[IO]
      case "block"               => CliCommand.CliBlockMenu.some.widen[CliCommand].pure[IO]
      case "help"                => CliCommand.CliHelp.some.widen[CliCommand].pure[IO]
      case "quit" | "exit" | "q" => CliCommand.CliQuit.some.widen[CliCommand].pure[IO]
      case other                 => c.println(s"Invalid option: $other").as(none[CliCommand])
    }.untilDefinedM
  }

  private def handleCommand(cliCommand: CliCommand): CommandT[IO, Unit] =
    cliCommand match {
      case CliCommand.CliAccountMenu     => AccountMenuCommand[IO]
      case CliCommand.CliTransactionMenu => TransactionMenuCommand[IO]
      case CliCommand.CliBlockMenu       => BlockMenuCommand[IO]
      case CliCommand.CliHelp            => HelpCommand[IO](menu)
      case CliCommand.CliQuit            => QuitCommand[IO]
      case _                             => CommandT.commandTMonadThrow[IO].raiseError[Unit](new IllegalStateException("handle cli command miss match!"))
    }

  private val cliResource: Resource[IO, Unit] = {
    Sync[IO]
      .defer(
        write[IO]("CLI:").value >>
          readCommand
            .flatMap(c => handleCommand(c))
            .value
            .flatMapOrKeep {
              case MenuTransaction => handleCommand(CliCommand.CliTransactionMenu).value
              case MenuBlock       => handleCommand(CliCommand.CliBlockMenu).value
              case MenuAccount     => handleCommand(CliCommand.CliAccountMenu).value
            }
            .iterateWhile(_ != Command.Exit)
            .void
      )
      .toResource
  }

  def run: IO[Unit] = {
    Security.addProvider(new BouncyCastleProvider())
    cliResource.useForever
  }

}
