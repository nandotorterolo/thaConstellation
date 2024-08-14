package io.github.nandotorterolo.node.service

import java.security.PrivateKey
import java.security.PublicKey

import cats.data.EitherT
import cats.effect.Async
import cats.implicits._
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.node.interfaces.ServerCredentials

class ServerCredentialsImpl[F[_]: Async](cripto: Cripto[F]) extends ServerCredentials[F] {

  /**
   * TODO, inject an application conf to retrieve path of the private key
   */
  override val getPrivateKey: F[Either[ModelThrowable, PrivateKey]] =
    EitherT(
      Async[F]
        .blocking(this.getClass.getClassLoader.getResourceAsStream("server").readAllBytes())
        .attempt
    ).leftMap(_ => Message("Cannot read the private key"))
      .flatMapF(bytes => cripto.privateKey[F](bytes.toVector))
      .leftWiden[ModelThrowable]
      .value

  /**
   * TODO, inject an application conf to retrieve path of the private key
   */
  override val getPublicKey: F[Either[ModelThrowable, PublicKey]] =
    EitherT(
      Async[F]
        .blocking(this.getClass.getClassLoader.getResourceAsStream("server.pub").readAllBytes())
        .attempt
    )
      .leftMap(_ => Message("Cannot read the public key"))
      .flatMapF(bytes => cripto.publicKey[F](bytes.toVector))
      .value

}

object ServerCredentialsImpl {
  def build[F[_]: Async](
      cripto: Cripto[F]
  ): ServerCredentials[F] =
    new ServerCredentialsImpl(cripto)
}
