package cli.commands

import cats.data.EitherT
import cats.effect.std.Console
import cats.effect.Async
import cats.implicits._
import cli._
import fs2.io.file.NoSuchFileException
import fs2.io.file.Path
import fs2.Chunk
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.models.Account
import io.github.nandotorterolo.models.AccountSigned
import io.github.nandotorterolo.models.Address
import io.github.nandotorterolo.models.ModelThrowable
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.`Content-Type`
import scodec.Codec

class RegistrationCommand[F[_]: Async: Console](cripto: Cripto[F], path: Path) {

  private val command: CommandT[F, Unit] = {
    val res = for {
      _ <- write[F]("Lets register an Address")

      keyName <- readParameter[F, String]("Private Key name", List.empty)

      privateKeyContent <- readFile(path / s"$keyName")
      privateKey        <- CommandT.liftF(EitherT(cripto.privateKey(privateKeyContent)).rethrowT)

      publicKeyContent <- readFile(path / s"$keyName.pub")
      publicKey        <- CommandT.liftF(EitherT(cripto.publicKey(publicKeyContent)).rethrowT)

      address = Address(publicKey)
      account = Account(address = address, balance = 0d, latestUsedNonce = 0)
      accountSigned <- CommandT.liftF(account.sign(privateKey)(cripto).rethrow)

      _ <- readYesNo("send registration?")(yesChoice(accountSigned), noChoice)
    } yield ()
    res
      .handleErrorWith {
        case fe: NoSuchFileException => write[F](show"File not found: ${fe.getMessage}")
        case m: ModelThrowable       => write[F](show"Error: $m")
        case e: Throwable            => write[F](s"Error: ${e.getMessage}")
      }
      .subflatMap(_ => Command.Menu)
  }

  def noChoice: CommandT[F, Unit] = write[F](s"bye!")

  private def yesChoice(accountSigned: AccountSigned): CommandT[F, Unit] = {

    val accountSignedEncoded = Codec[AccountSigned].encode(accountSigned).require.bytes.toArray
    val entityBody           = fs2.Stream.chunk(Chunk.array(accountSignedEncoded)).covary[F]

    val request: Request[F] =
      Request[F]()
        .withUri(registerUri)
        .withMethod(Method.POST)
        .withContentType(`Content-Type`(MediaType.application.`octet-stream`))
        .withBodyStream(entityBody)

    CommandT.liftF {
      EmberClientBuilder.default[F].build.use { client =>
        client
          .expectOr[String](request)(r =>
            r.body
              .through(fs2.text.utf8.decode)
              .compile
              .string
              .map(body => s"${r.status.code} : $body")
              .map(ModelThrowable.Message)
          )
          .recover { case ex: Exception => s"Error $ex ${ex.getMessage}" }
          .flatMap(s => Console[F].println(s"$s"))
      }
    }
  }
}

object RegistrationCommand {
  def apply[F[_]: Async: Console](implicit cripto: Cripto[F], path: Path): CommandT[F, Unit] =
    new RegistrationCommand[F](cripto, path).command
}
