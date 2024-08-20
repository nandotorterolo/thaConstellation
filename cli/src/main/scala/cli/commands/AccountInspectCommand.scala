package cli.commands

import cats.data.EitherT
import cats.effect.std.Console
import cats.effect.Async
import cats.implicits._
import cli._
import fs2.io.file.NoSuchFileException
import fs2.io.file.Path
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.models.Address
import io.github.nandotorterolo.models.ModelThrowable

class AccountInspectCommand[F[_]: Async: Console](cripto: Cripto[F], path: Path) {

  private val command: CommandT[F, Unit] = {
    val res = for {
      _       <- write[F]("Lets Inspect a PUBLIC key")
      keyName <- readParameter[F, String]("Public Key name", List("a.pub"))

      publicKeyContent <- readFile(Path(s"$path/$keyName"))
      publicKey        <- CommandT.liftF(EitherT(cripto.publicKey(publicKeyContent)).rethrowT)

      address = Address(publicKey)
      _ <- write[F](show"Your address: $address")
    } yield ()
    res
      .handleErrorWith {
        case fe: NoSuchFileException => write[F](show"File not found: ${fe.getMessage}")
        case m: ModelThrowable       => write[F](show"Error: $m")
        case e: Throwable            => write[F](s"Error: ${e.getMessage}")
      }
      .subflatMap(_ => Command.MenuAccount)
  }
}

object AccountInspectCommand {
  def apply[F[_]: Async: Console](implicit cripto: Cripto[F], path: Path): CommandT[F, Unit] =
    new AccountInspectCommand[F](cripto, path).command
}
