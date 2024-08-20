package cli.commands

import cats.data.EitherT
import cats.effect.std.Console
import cats.effect.Async
import cats.implicits._
import cli._
import fs2.io.file.Path
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.models.Address

/**
 * Generate a Key Pair command
 */
class AccountGenerateCommand[F[_]: Async: Console](cripto: Cripto[F], path: Path) {

  private val command: CommandT[F, Unit] = {
    val res = for {
      _  <- write[F]("Lets generate a private-public key pair.")
      id <- readParameter[F, String]("Key name", List.empty)

      kp <- CommandT.liftF(EitherT(cripto.getKeyPair).rethrowT)

      _ <- writeFile(path)(kp.getPrivate.getEncoded)(s"$id")
      _ <- write[F](s"A private file was saved on $path/$id")

      _ <- writeFile(path)(kp.getPublic.getEncoded)(s"$id.pub")
      _ <- write[F](s"A public file was saved on $path/$id.pub")

      address = Address(kp.getPublic)
      _ <- write[F](show"Your base58 AddressId: $address")
      _ <- write[F](show"You could continue with registration step")

    } yield ()
    res
      .handleErrorWith { t: Throwable =>
        CommandT.liftF(Console[F].println(s"Error ${t.getMessage}"))
      }
      .subflatMap(_ => Command.MenuAccount)
  }
}

object AccountGenerateCommand {
  def apply[F[_]: Async: Console](implicit cripto: Cripto[F], path: Path): CommandT[F, Unit] =
    new AccountGenerateCommand[F](cripto, path).command
}
