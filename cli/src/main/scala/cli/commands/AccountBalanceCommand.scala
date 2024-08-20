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
import io.github.nandotorterolo.models.AddressId
import io.github.nandotorterolo.models.AddressIdSigned
import io.github.nandotorterolo.models.ModelThrowable
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.`Content-Type`

class AccountBalanceCommand[F[_]: Async: Console](cripto: Cripto[F], pathConfig: Path) {

  private val command: CommandT[F, Unit] = {
    val res = for {
      _ <- write[F]("Get details from an Address")

      keyName           <- readParameter[F, String]("Private Key:", List("id"))
      privateKeyContent <- readFile(Path(s"$pathConfig/$keyName"))
      privateKey        <- CommandT.liftF(EitherT(cripto.privateKey[F](privateKeyContent)).rethrowT)

      publicKeyContent <- readFile(Path(s"$pathConfig/$keyName.pub"))
      publicKey        <- CommandT.liftF(EitherT(cripto.publicKey[F](publicKeyContent)).rethrowT)

      addressId = AddressId(publicKey)

      addressIdSigned <- CommandT.liftF(addressId.sign[F](privateKey)(cripto)).rethrow

      // pre send validation.assuming that the public keys are in the same Path that private

      _ <- CommandT.liftF(addressIdSigned.validate[F](publicKey)(cripto)).flatMap {
        case Right(true) => CommandT.liftF(Async[F].unit)
        case _ =>
          CommandT
            .commandTMonadThrow[F]
            .raiseError[Unit](new IllegalStateException("Invalid signature"))
      }

      _ <- getBalance(addressIdSigned)

    } yield ()
    res
      .handleErrorWith {
        case fe: NoSuchFileException => write[F](show"File not found: ${fe.getMessage}")
        case m: ModelThrowable       => write[F](show"Error: $m")
        case e: Throwable            => write[F](s"Error: ${e.getMessage}")
      }
      .subflatMap(_ => Command.MenuAccount)
  }

  private def getBalance(addressIdSigned: AddressIdSigned): CommandT[F, Unit] = {
    val entityBody: EntityBody[F] =
      fs2.Stream
        .chunk(Chunk.array(AddressIdSigned.codec.encode(addressIdSigned).require.bytes.toArray))
        .covary[F]

    val request: Request[F] =
      Request[F]()
        .withUri(balanceUri)
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
          .recover { case ex: Exception => s"Error $ex" }
          .flatMap(s => Console[F].println(s"$s"))
      }
    }
  }

}

object AccountBalanceCommand {
  def apply[F[_]: Async: Console](implicit cripto: Cripto[F], path: Path): CommandT[F, Unit] =
    new AccountBalanceCommand[F](cripto, path).command
}
