package io.github.nandotorterolo.node

import cats.effect.IO
import cats.effect.IOApp

object Main extends IOApp.Simple {
  val run = NodeServer.run[IO]
}
