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
import io.github.nandotorterolo.models._
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.`Content-Type`
import scodec.bits.ByteVector
import scodec.Codec

class InspectTransactionCommand[F[_]: Async: Console](cripto: Cripto[F], path: Path) {

  private val command: CommandT[F, Unit] = {
    val res = for {
      _ <- write[F]("Inspect transaction information")
      transactionIdStr <- readParameter[F, String](
        "Transaction Id",
        List("5YBCf1CzwJocQeYhYVfoeyBwAL3VeW6wpXkQLDbjU5L6NVDxLvcTm6hidtQmCj8XZZam2qRzwX19u9k1rdmAdWpB")
      )

      keyName <- readParameter[F, String]("Private Key name", List.empty)

      privateKeyContent <- readFile(path / s"$keyName")
      privateKey        <- CommandT.liftF(EitherT(cripto.privateKey(privateKeyContent)).rethrowT)

      publicKeyContent <- readFile(path / s"$keyName.pub")
      publicKey        <- CommandT.liftF(EitherT(cripto.publicKey(publicKeyContent)).rethrowT)
      address = Address(publicKey)

      transactionId          = TransactionId(ByteVector.fromValidBase58(transactionIdStr))
      transactionIdAddressId = TransactionIdAddressId(transactionId, address.addressId)
      transactionIdAddressIdSigned <- CommandT.liftF(transactionIdAddressId.sign(privateKey)(cripto).rethrow)

      _ <- getTransaction(transactionIdAddressIdSigned)

    } yield ()
    res
      .handleErrorWith {
        case fe: NoSuchFileException => write[F](show"File not found: ${fe.getMessage}")
        case m: ModelThrowable       => write[F](show"Error: $m")
        case e: Throwable            => write[F](s"Error: ${e.getMessage}")
      }
      .subflatMap(_ => Command.Menu)
  }

  private def getTransaction(transactionIdAddressIdSigned: TransactionIdAddressIdSigned): CommandT[F, Unit] = {
    val chunk = Chunk.array(Codec[TransactionIdAddressIdSigned].encode(transactionIdAddressIdSigned).require.bytes.toArray)

    val entityBody: EntityBody[F] = fs2.Stream.chunk(chunk).covary[F]

    val request: Request[F] =
      Request[F]()
        .withUri(txInspectUri)
        .withMethod(Method.POST)
        .withContentType(`Content-Type`(MediaType.application.`octet-stream`))
        .withBodyStream(entityBody)

    CommandT.liftF {
      EmberClientBuilder.default[F].build.use { client =>
        client
          .fetchAs[String](request)
          .recover { case ex: Exception => s"Error $ex" }
          .flatMap(s => Console[F].println(s"OK: $s"))
      }
    }
  }

}

object InspectTransactionCommand {
  def apply[F[_]: Async: Console](implicit cripto: Cripto[F], path: Path): CommandT[F, Unit] =
    new InspectTransactionCommand[F](cripto, path).command
}
