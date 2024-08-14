package io.github.nandotorterolo.node.routes

import cats.effect.Async
import cats.implicits._
import io.github.nandotorterolo.node.service.HealthService
import org.http4s.dsl.Http4sDsl
import org.http4s.HttpRoutes

object HealthRoute {

  def route[F[_]: Async](H: HealthService[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "health" / name =>
        for {
          greeting <- H.hello(HealthService.Name(name))
          resp     <- Ok(greeting)
        } yield resp
    }
  }

}
