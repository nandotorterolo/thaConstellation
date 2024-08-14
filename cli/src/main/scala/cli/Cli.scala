package cli

import java.security.Security

import cats.effect.kernel.Sync
import cats.effect.std.Console
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.implicits._
import cli.commands._
import com.typesafe.config.ConfigFactory
import fs2.io.file.Path
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import org.bouncycastle.jce.provider.BouncyCastleProvider

object Cli extends IOApp.Simple {

  sealed abstract class CliCommand
  object CliCommand {
    case object CliQuit         extends CliCommand
    case object CliGenerateKP   extends CliCommand
    case object CliTransaction  extends CliCommand
    case object CliRegistration extends CliCommand
    case object CliBalance      extends CliCommand
    case object CliBlock        extends CliCommand
    case object CliInspectTx    extends CliCommand
    case object CliHelp         extends CliCommand
    case object CliInspect      extends CliCommand
  }

  private implicit val c: Console[IO] = Console.make[IO]

  private implicit val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]
  private implicit val pathConfig: Path   = Path(ConfigFactory.load().getString("cli.path"))

  private val readCommand: CommandT[IO, CliCommand] = {
    val res = for {
      _ <- write[IO]("Enter option. [inspect | gen | tx | reg | bal | getBlock | getTx | help | quit]")
      _ = Security.addProvider(new BouncyCastleProvider())
      r <- read[IO]
    } yield r
    res.semiflatMap {
      case "quit" | "exit"           => CliCommand.CliQuit.some.widen[CliCommand].pure[IO]
      case "gen" | "generateKeyPair" => CliCommand.CliGenerateKP.some.widen[CliCommand].pure[IO]
      case "tx" | "transaction"      => CliCommand.CliTransaction.some.widen[CliCommand].pure[IO]
      case "reg" | "registration"    => CliCommand.CliRegistration.some.widen[CliCommand].pure[IO]
      case "bal" | "balance"         => CliCommand.CliBalance.some.widen[CliCommand].pure[IO]
      case "block" | "getBlock"      => CliCommand.CliBlock.some.widen[CliCommand].pure[IO]
      case "getTx" | "inspectTx"     => CliCommand.CliInspectTx.some.widen[CliCommand].pure[IO]
      case "inspect"                 => CliCommand.CliInspect.some.widen[CliCommand].pure[IO]
      case "help"                    => CliCommand.CliHelp.some.widen[CliCommand].pure[IO]
      case other                     => c.println(s"Invalid option: $other").as(none[CliCommand])
    }.untilDefinedM

  }

  private def handleCommand(cliCommand: CliCommand): CommandT[IO, Unit] =
    cliCommand match {
      case CliCommand.CliQuit         => QuitCommand[IO]
      case CliCommand.CliGenerateKP   => GenerateCommand[IO]
      case CliCommand.CliTransaction  => TransactionCommand[IO]
      case CliCommand.CliRegistration => RegistrationCommand[IO]
      case CliCommand.CliBalance      => BalanceCommand[IO]
      case CliCommand.CliBlock        => BlockCommand[IO]
      case CliCommand.CliInspectTx    => InspectTransactionCommand[IO]
      case CliCommand.CliHelp         => HelpCommand[IO]
      case CliCommand.CliInspect      => InspectCommand[IO]
    }

  private val cliResource: Resource[IO, Unit] = {
    Sync[IO]
      .defer(
        write[IO]("CLI:").value >>
          (readCommand >>= handleCommand).value
            .iterateWhile(_ != Command.Exit)
            .void
      )
      .toResource
  }

  def run: IO[Unit] = cliResource.useForever

}
