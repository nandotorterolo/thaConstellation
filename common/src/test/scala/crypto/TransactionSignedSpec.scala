package crypto

import java.security.Security

import cats.effect.IO
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models.Address
import io.github.nandotorterolo.models.Transaction
import munit.CatsEffectSuite
import org.bouncycastle.jcajce.provider.digest.SHA3
import org.bouncycastle.jce.provider.BouncyCastleProvider

class TransactionSignedSpec extends CatsEffectSuite {

  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  test("Sign Transaction") {
    val r = for {
      kpSource <- cripto.getKeyPair.rethrow

      publicKeySource = kpSource.getPublic
      addressSource   = Address(publicKeySource).addressId

      kpDestination <- cripto.getKeyPair.rethrow
      addressDestination = Address(kpDestination.getPublic).addressId

      tx      = Transaction(addressSource, addressDestination, amount = 100d, nonce = 0)
      message = Transaction.codec.encode(tx).require
      hash    = new SHA3.Digest512().digest(message.toByteArray)

      signature <- cripto.getSignature(hash, kpSource.getPrivate).rethrow

      validateRes <- cripto.validateSignature(hash, publicKeySource, signature).rethrow

    } yield validateRes

    assertIOBoolean(r)

  }

  test("Sign Transaction using Signable") {
    val r = for {
      kpSource <- cripto.getKeyPair.rethrow

      publicKeySource = kpSource.getPublic
      addressSource   = Address(publicKeySource).addressId

      kpDestination <- cripto.getKeyPair.rethrow
      addressDestination = Address(kpDestination.getPublic).addressId

      tx = Transaction(addressSource, addressDestination, amount = 100d, nonce = 0)
      txSigned <- tx.sign(kpSource.getPrivate)(cripto).rethrow

      validateRes <- txSigned.validate(publicKeySource)(cripto).rethrow
    } yield validateRes

    assertIOBoolean(r)

  }
}
