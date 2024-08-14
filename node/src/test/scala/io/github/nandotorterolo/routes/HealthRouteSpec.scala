package io.github.nandotorterolo.routes

import cats.effect.IO
import io.github.nandotorterolo.node.routes.HealthRoute
import io.github.nandotorterolo.node.service.HealthService
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.implicits._

class HealthRouteSpec extends CatsEffectSuite {

  private[this] val retHealth: IO[Response[IO]] = {
    val getHW      = Request[IO](Method.GET, uri"/health/world")
    val helloWorld = HealthService.build[IO]
    HealthRoute.route(helloWorld).orNotFound(getHW)
  }

  test("Health returns status code 200") {
    assertIO(retHealth.map(_.status), Status.Ok)
  }

  test("Health returns hello world message") {
    assertIO(retHealth.flatMap(_.as[String]), "{\"message\":\"Hello, world\"}")
  }

}
