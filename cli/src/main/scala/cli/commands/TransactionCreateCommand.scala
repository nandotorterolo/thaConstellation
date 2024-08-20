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
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.Transaction
import io.github.nandotorterolo.models.TransactionSigned
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request

class TransactionCreateCommand[F[_]: Async: Console](cripto: Cripto[F], path: Path) {

  private val command: CommandT[F, Unit] = {
    val res = for {
      _ <- write[F]("Lets create a Transaction")

      destinationAddress <- readParameter[F, String]("<DESTINATION> Address", List("3FTBbJzQptLTbNVZyCvNQeScRcSQ"))
      amount             <- readParameter[F, Double]("Amount to transfer", List("100", "3.4"))
      nonce              <- readParameter[F, Int]("nonce", List("1", "2", "3", "4"))

      _ <- write[F]("Lets sign the Transaction")

      keyName                 <- readParameter[F, String]("<SOURCE> Private Key name", List(""))
      privateKeySourceContent <- readFile(path / s"$keyName")
      privateSourceKey        <- CommandT.liftF(EitherT(cripto.privateKey(privateKeySourceContent)).rethrowT)

      publicKeySourceContent <- readFile(path / s"$keyName.pub")
      publicKeySource <- CommandT.liftF(
        EitherT(cripto.publicKey[F](publicKeySourceContent)).rethrowT
      )
      sourceAddressId = AddressId(publicKeySource)
      destAddressId   = AddressId(destinationAddress)
      transaction     = Transaction(sourceAddressId, destAddressId, amount, nonce)

      transactionSigned <- CommandT.liftF(transaction.sign(privateSourceKey)(cripto)).rethrow

      // pre send validation. assuming that the public keys are in the same Path that private
      _ <- CommandT
        .liftF(transactionSigned.validate[F](publicKeySource)(cripto))
        .flatMap {
          case Right(true) => CommandT.liftF(Async[F].unit)
          case _ =>
            CommandT
              .commandTMonadThrow[F]
              .raiseError[Unit](new IllegalStateException("Validation Signature error!"))
        }

      _ <- readYesNo("send transaction?")(yesChoice(transactionSigned), noChoice)

    } yield ()
    res
      .handleErrorWith {
        case fe: NoSuchFileException => write[F](show"File not found: ${fe.getMessage}")
        case m: ModelThrowable       => write[F](show"Error: $m")
        case e: Throwable            => write[F](s"Error: ${e.getMessage}")
      }
      .subflatMap(_ => Command.MenuTransaction)
  }

  def noChoice: CommandT[F, Unit] = write[F](s"bye!")

  private def yesChoice(transactionSigned: TransactionSigned): CommandT[F, Unit] = {

    val body       = TransactionSigned.codec.encode(transactionSigned).require.bytes.toArray
    val entityBody = fs2.Stream.chunk(Chunk.array(body)).covary[F]

    val request: Request[F] =
      Request[F]()
        .withUri(broadcastUri)
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

object TransactionCreateCommand {
  def apply[F[_]: Async: Console](implicit cripto: Cripto[F], path: Path): CommandT[F, Unit] =
    new TransactionCreateCommand[F](cripto, path).command
}
