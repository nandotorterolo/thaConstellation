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

  /**
   * From: https://security.stackexchange.com/questions/270987/is-backing-up-the-seed-used-to-generate-an-rsa-key-pair-a-secure-practice
   * The seed of the randomness generator alone isn't sufficient to reconstruct the key.
   * You'd also have to know the exact code which produced the key, because every tool and even different versions of the same tool can behave differently.
   * For example, if you generated a key several years ago, the seed won't help you much today. To reconstruct the key, you'll need the exact same tool with the exact same version as before.
   *
   * So while this backup technique is technically possible, it's extremely brittle, because you cannot restore the backup without specific code.
   * You'll either have to store the entire code together with the seed (which is much bigger and more complex than a simple RSA key pair),
   * or your backup will be highly susceptible to technology rot, because the tools and their versions we use today will all be obsolete in a few years.
   *
   * Simply backup the encrypted private key.
   *
   * https://www.rfc-editor.org/rfc/rfc6979
   * Deterministic Usage of the Digital Signature Algorithm (DSA) and Elliptic Curve Digital Signature Algorithm (ECDSA)
   *
   * @see  clojure implementation https://github.com/SixtantIO/rfc6979
   * @see   ECDSA cryptographic algorithm used in the fabric system has backdoor security risks.  https://www.researchgate.net/publication/370315861_Fabric_Blockchain_Design_Based_on_Improved_SM2_Algorithm
   *
   * https://cryptobook.nakov.com/digital-signatures/ecdsa-sign-verify-messages
   * The public key recovery from the ECDSA signature is very useful in bandwidth constrained or storage constrained environments (such as blockchain systems), when transmission or storage of the public keys cannot be afforded.
   * For example, the Ethereum blockchain uses extended signatures {r, s, v} for the signed transactions on the chain to save storage and bandwidth.
   *
   * if we use the same random number for at least two signatures, we can recover the private key. Instead of using a random number, RFC6979 uses HMAC-SHA256(private_key, message) in order to overcome the private key leakage problem.
   * The signature then becomes deterministic, and where we always produce the same output for a given set of inputs. In the same runs, we use a k
   * value of 9, and which will always produces the same message and set of keys. In this way, the output is deterministic.
   *
   *   Practical Examples in Blockchain:
   *   Bitcoin:
   *   SHA-256: Used in the mining process to solve complex mathematical puzzles. Each block’s hash is derived using SHA-256, ensuring the block’s data integrity.
   *   ECDSA: Utilized to sign transactions, ensuring that only the owner of a Bitcoin wallet can initiate transactions from that wallet. Secp256k1
   *   Ethereum:
   *   Keccak-256: A variant of SHA-3, used in Ethereum for hashing. It provides robust security and is integral to Ethereum’s proof-of-work algorithm and account addressing.
   *   ECDSA: Employed for transaction signing, similar to Bitcoin, ensuring secure and verifiable transactions. Secp256k1
   *   By understanding these cryptographic algorithms and their applications in blockchain, IT professionals can appreciate the mechanisms that ensure the security and trustworthiness of blockchain networks. These algorithms form the foundation upon which blockchain’s decentralized and tamper-proof nature is built.
   *
   *   Polkadot uses Schnorrkel/Ristretto x25519 ("sr25519") as its key derivation and signing algorithm.  Sr25519 is based on the same underlying Curve25519 as its EdDSA counterpart, Ed25519.
   *
   * Question: In this implementation Public keys are being save in the server, lets suppose storage is not a problem, is it better for performance than recovery it from a signature.
   * @return
   */
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
