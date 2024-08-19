package io.github.nandotorterolo.node

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Sync
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

object Main extends IOApp.Simple {

  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  val run: IO[Nothing] = NodeServer.run[IO]
}
