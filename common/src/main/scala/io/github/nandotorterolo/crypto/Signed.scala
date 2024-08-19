package io.github.nandotorterolo.crypto

import java.security.PublicKey

import scala.util.Try

import cats.data.EitherT
import cats.effect.Sync
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.Message
import org.bouncycastle.jcajce.provider.digest.SHA3
import scodec.bits.ByteVector
import scodec.Codec

trait Signed[A] {

  def hash: Hash
  def signature: Signature
  def message: A

  def validate[F[_]: Sync](
      publicKey: PublicKey
  )(crypto: Cripto[F])(implicit codec: Codec[A]): F[Either[ModelThrowable, Boolean]] = {
    (for {
      encode <- EitherT
        .fromEither[F](codec.encode(message).toEither)
        .leftMap(err => Message(s"$err"))

      hashFromEncode <- EitherT.fromEither[F](
        Try(new SHA3.Digest512().digest(encode.toByteArray)).toEither.left.map(err => Message(s"$err"))
      )

      _ <- EitherT.cond(hash.value.compareTo(ByteVector(hashFromEncode)) == 0, (), Message(s"Hash provided and calculated does not match."))

      isValid <- EitherT(crypto.validateSignature(hashFromEncode, publicKey, signature.value.toArray))

    } yield isValid).value
  }

}
