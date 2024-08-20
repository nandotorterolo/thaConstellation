package cli.commands

import cats.effect.std.Console
import cats.effect.Async
import cats.implicits._
import cli._
import fs2.Chunk
import io.github.nandotorterolo.models.BlockId
import io.github.nandotorterolo.models.ModelThrowable
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.`Content-Type`
import scodec.bits.ByteVector

class BlockByIdCommand[F[_]: Async: Console] {

  private val command: CommandT[F, Unit] = {
    val res = for {
      _          <- write[F]("Get block information")
      blockIdStr <- readParameter[F, String]("Block Id", List("5YBCf1CzwJocQeYhYVfoeyBwAL3VeW6wpXkQLDbjU5L6NVDxLvcTm6hidtQmCj8XZZam2qRzwX19u9k1rdmAdWpB"))

      blockId = BlockId(ByteVector.fromValidBase58(blockIdStr))

      _ <- getBlock(blockId)

    } yield ()
    res
      .handleErrorWith {
        case m: ModelThrowable => write[F](show"Error: $m")
        case e: Throwable      => write[F](s"Error: ${e.getMessage}")
      }
      .subflatMap(_ => Command.MenuBlock)
  }

  private def getBlock(blockId: BlockId): CommandT[F, Unit] = {
    val entityBody: EntityBody[F] =
      fs2.Stream
        .chunk(Chunk.array(BlockId.codec.encode(blockId).require.bytes.toArray))
        .covary[F]

    val request: Request[F] =
      Request[F]()
        .withUri(blockUri)
        .withMethod(Method.POST)
        .withContentType(`Content-Type`(MediaType.application.`octet-stream`))
        .withBodyStream(entityBody)

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

object BlockByIdCommand {
  def apply[F[_]: Async: Console]: CommandT[F, Unit] =
    new BlockByIdCommand[F].command
}
