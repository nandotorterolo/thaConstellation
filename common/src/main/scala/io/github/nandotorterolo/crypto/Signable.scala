package io.github.nandotorterolo.crypto

import java.security.PrivateKey

import scala.util.Try

import cats.data.EitherT
import cats.effect.Sync
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.Message
import org.bouncycastle.jcajce.provider.digest.SHA3
import scodec.Codec

trait Signable[A, S] {

  val encodeMe: A

  def build: (Hash, Signature, A) => S

  /**
   * Sing a model class
   * @param privateKey private Key
   * @param cripto cripto implementation used
   * @param f a function that receives (Hash, Signature, A): F
   * @param codec Type A must implement a codec
   * @tparam F F context
   * @tparam T  return type, example: AddressIdSigned
   * @example """ addressIdSigned <- addressId.signT(kp.getPrivate)(cripto, AddressIdSigned(_,_,_)) """
   */
  def sign[F[_]: Sync](
      privateKey: PrivateKey
  )(cripto: Cripto[F])(implicit codec: Codec[A]): F[Either[ModelThrowable, S]] = {
    (for {
      message <- EitherT
        .fromEither[F](codec.encode(encodeMe).toEither)
        .leftMap(err => Message(s"Signable Encode: $err"))

      hash <- EitherT.fromEither[F](
        Try(new SHA3.Digest512().digest(message.toByteArray)).toEither.left.map(err => Message(s"Signable Digest: $err"))
      )

      signature <- EitherT(cripto.getSignature(hash, privateKey))

    } yield build(Hash(hash), Signature(signature), encodeMe)).value
  }

}
