package cli.commands

import cats.effect.std.Console
import cats.effect.Async
import cats.implicits._
import cli._
import io.github.nandotorterolo.models.ModelThrowable
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder

class BlockBySeqNumberCommand[F[_]: Async: Console] {

  private val command: CommandT[F, Unit] = {
    val res = for {
      _         <- write[F]("Get block information")
      seqNumber <- readParameter[F, Int]("Seq Number", List("0", "1", "N"))

      _ <- getBlockBySeqNumber(seqNumber)

    } yield ()
    res
      .handleErrorWith {
        case m: ModelThrowable => write[F](show"Error: $m")
        case e: Throwable      => write[F](s"Error: ${e.getMessage}")
      }
      .subflatMap(_ => Command.MenuBlock)
  }

  private def getBlockBySeqNumber(seqNumber: Int): CommandT[F, Unit] = {

    val request: Request[F] =
      Request[F]()
        .withUri(blockUri.addSegment(seqNumber))
        .withMethod(Method.GET)

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

object BlockBySeqNumberCommand {
  def apply[F[_]: Async: Console]: CommandT[F, Unit] =
    new BlockBySeqNumberCommand[F].command
}
