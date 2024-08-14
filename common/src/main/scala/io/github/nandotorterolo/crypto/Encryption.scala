package io.github.nandotorterolo.crypto

import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.Signature

import cats.effect.Sync
import cats.implicits._
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.Message
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.ECNamedCurveTable

trait Encryption[F[_]] {

  def keyFactoryInstance: KeyFactory

  def getKeyPair: F[Either[ModelThrowable, KeyPair]]

  def getSignature(
      content: Array[Byte],
      privateKey: PrivateKey
  ): F[Either[ModelThrowable, Array[Byte]]]

  def validateSignature(
      data: Array[Byte],
      publicKey: PublicKey,
      signature: Array[Byte]
  ): F[Either[ModelThrowable, Boolean]]
}

trait EncryptionUtil[F[_]] {
  self: Encryption[F] =>

  def privateKey[G[_]: Sync](content: Vector[Byte]): G[Either[Message, PrivateKey]] =
    Sync[G]
      .delay {
        keyFactoryInstance.generatePrivate(new PKCS8EncodedKeySpec(content.toArray))
      }
      .attempt
      .map(_.left.map(th => Message(s"Unable to create a Private key: ${th.getMessage}")))

  def publicKey[G[_]: Sync](content: Vector[Byte]): G[Either[ModelThrowable, PublicKey]] =
    Sync[G]
      .delay {
        keyFactoryInstance.generatePublic(new X509EncodedKeySpec(content.toArray))
      }
      .attempt
      .map(_.left.map(th => Message(s"Unable to create a Public key: ${th.getMessage}")))

}

class EcdsaBCEncryption[F[_]: Sync] extends Encryption[F] with EncryptionUtil[F] {

  Security.addProvider(new BouncyCastleProvider())

  private val Provider          = "BC"
  private val ProviderAlgorithm = "ECDSA"
  // inconsistent issues using  "SHA256withECDSA"
  private val SignatureAlgorithm = "SHA256withPLAIN-ECDSA"

  override val keyFactoryInstance: KeyFactory = KeyFactory.getInstance(ProviderAlgorithm, Provider)
  private val keyPairGeneratorInstance        = KeyPairGenerator.getInstance(ProviderAlgorithm, Provider)
  private val signatureInstance               = Signature.getInstance(SignatureAlgorithm, Provider)
  private val algorithmParameterSpec          = ECNamedCurveTable.getParameterSpec("B-571")

  def getKeyPair: F[Either[ModelThrowable, KeyPair]] =
    Sync[F]
      .blocking {
        keyPairGeneratorInstance.initialize(algorithmParameterSpec, new SecureRandom())
        keyPairGeneratorInstance.generateKeyPair()
      }
      .attempt
      .map(_.left.map(th => Message(th.getMessage)))

  override def getSignature(
      content: Array[Byte],
      privateKey: PrivateKey
  ): F[Either[ModelThrowable, Array[Byte]]] =
    Sync[F]
      .blocking {
        signatureInstance.initSign(privateKey)
        signatureInstance.update(content)
        signatureInstance.sign()
      }
      .attempt
      .map(_.left.map(th => Message(th.getMessage)))

  override def validateSignature(
      data: Array[Byte],
      publicKey: PublicKey,
      signature: Array[Byte]
  ): F[Either[ModelThrowable, Boolean]] =
    Sync[F]
      .blocking {
        signatureInstance.initVerify(publicKey)
        signatureInstance.update(data)
        signatureInstance.verify(signature)
      }
      .attempt
      .map(_.left.map(th => Message(th.getMessage)))

}

object EcdsaBCEncryption {
  def build[F[_]: Sync] = new EcdsaBCEncryption
}
