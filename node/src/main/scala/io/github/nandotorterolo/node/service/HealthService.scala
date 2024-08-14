package io.github.nandotorterolo.node.service

import cats.implicits._
import cats.Applicative
import io.circe.Encoder
import io.circe.Json
import org.http4s.circe._
import org.http4s.EntityEncoder

trait HealthService[F[_]] {
  def hello(n: HealthService.Name): F[HealthService.Greeting]
}

object HealthService {
  final case class Name(name: String) extends AnyVal

  /**
   * More generally you will want to decouple your edge representations from
   * your internal data structures, however this shows how you can
   * create encoders for your data.
   */
  final case class Greeting(greeting: String) extends AnyVal
  object Greeting {
    implicit val greetingEncoder: Encoder[Greeting] = new Encoder[Greeting] {
      final def apply(a: Greeting): Json = Json.obj(
        ("message", Json.fromString(a.greeting)),
      )
    }
    implicit def greetingEntityEncoder[F[_]]: EntityEncoder[F, Greeting] =
      jsonEncoderOf[F, Greeting]
  }

  def build[F[_]: Applicative]: HealthService[F] = new HealthService[F] {
    def hello(n: HealthService.Name): F[HealthService.Greeting] =
      Greeting("Hello, " + n.name).pure[F]
  }
}
