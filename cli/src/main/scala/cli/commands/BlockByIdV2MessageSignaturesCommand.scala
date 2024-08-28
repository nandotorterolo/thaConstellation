package cli.commands


import cats.data.EitherT
import cats.effect.std.Console
import cats.effect.Async
import cats.implicits._
import cli._
import fs2.io.file.Path
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.models.AddressId
import io.github.nandotorterolo.models.AddressIdSigned
import io.github.nandotorterolo.models.BlockId
import io.github.nandotorterolo.models.ModelThrowable
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.ci.CIString
import scodec.bits.ByteVector

class BlockByIdV2MessageSignaturesCommand[F[_]: Async: Console](cripto: Cripto[F], pathConfig: Path) {

  private val command: CommandT[F, Unit] = {
    val res = for {
      _          <- write[F]("Get block information")
      blockIdStr <- readParameter[F, String]("Block Id", List("5YBCf1CzwJocQeYhYVfoeyBwAL3VeW6wpXkQLDbjU5L6NVDxLvcTm6hidtQmCj8XZZam2qRzwX19u9k1rdmAdWpB"))
      blockId = BlockId(ByteVector.fromValidBase58(blockIdStr))

      keyName           <- readParameter[F, String]("Private Key:", List("id"))
      privateKeyContent <- readFile(Path(s"$pathConfig/$keyName"))
      privateKey        <- CommandT.liftF(EitherT(cripto.privateKey[F](privateKeyContent)).rethrowT)

      publicKeyContent <- readFile(Path(s"$pathConfig/$keyName.pub"))
      publicKey        <- CommandT.liftF(EitherT(cripto.publicKey[F](publicKeyContent)).rethrowT)

      addressId = AddressId(publicKey)

      addressIdSigned <- CommandT.liftF(addressId.sign[F](privateKey)(cripto)).rethrow

      _ <- getBlock(blockId, addressIdSigned)

    } yield ()
    res
      .handleErrorWith {
        case m: ModelThrowable => write[F](show"Error: $m")
        case e: Throwable      => write[F](s"Error: ${e.getMessage}")
      }
      .subflatMap(_ => Command.MenuBlock)
  }

  private def getBlock(blockId: BlockId, addressIdSigned: AddressIdSigned): CommandT[F, Unit] = {

    val request: Request[F] =
      Request[F]()
        .withUri(blockUriV2 / blockId.value.toBase58)
        .withMethod(Method.GET)
        .withHeaders(
          Headers(
            Header.Raw(CIString("Content-Digest"), s"SHA256withPLAIN-ECDSA=:${addressIdSigned.hash.value.toBase64}:"),
            Header.Raw(CIString("Signature-Input"), s"""sig1=("content-digest");keyid="${addressIdSigned.message.value.toBase64}""""),
            Header.Raw(CIString("Signature"), s"sig1=:${addressIdSigned.signature.value.toBase64}:")
          )
        )

    CommandT.liftF {
      EmberClientBuilder.default[F].build.use { client =>
        client
          .fetchAs[String](request)
          .recover { case ex: Exception => s"Error $ex" }
          .flatMap(s => Console[F].println(s"$s"))
      }
    }
  }

}

object BlockByIdV2MessageSignaturesCommand {
  def apply[F[_]: Async: Console](implicit cripto: Cripto[F], pathConfig: Path): CommandT[F, Unit] =
    new BlockByIdV2MessageSignaturesCommand[F](cripto, pathConfig).command
}
