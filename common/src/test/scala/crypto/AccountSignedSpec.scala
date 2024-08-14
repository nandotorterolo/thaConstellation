package crypto

import java.security.Security

import cats.effect.IO
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signature
import io.github.nandotorterolo.models.Account
import io.github.nandotorterolo.models.AccountSigned
import io.github.nandotorterolo.models.Address
import munit.CatsEffectSuite
import org.bouncycastle.jcajce.provider.digest.SHA3
import org.bouncycastle.jce.provider.BouncyCastleProvider

class AccountSignedSpec extends CatsEffectSuite {

  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  test("Sign Account") {

    val r = for {
      kp <- cripto.getKeyPair.rethrow
      address = Address(kp.getPublic)
      account = Account(address = address, balance = 0d, latestUsedNonce = 0)
      message = Account.codec.encode(account).require
      hash    = new SHA3.Digest512().digest(message.toByteArray)
      signature <- cripto.getSignature(hash, kp.getPrivate).rethrow
      accountSigned    = AccountSigned(Hash(hash), Signature(signature), account)
      publicKeyAccount = accountSigned.message.address.publicKey.toArray.toVector
      publicKey   <- cripto.publicKey[IO](publicKeyAccount).rethrow
      validateRes <- cripto.validateSignature(hash, publicKey, signature).rethrow
    } yield validateRes

    assertIOBoolean(r)

  }

  test("Sign Account using Signable") {
    val r = for {
      kp <- cripto.getKeyPair.rethrow
      address = Address(kp.getPublic)
      account = Account(address = address, balance = 0d, latestUsedNonce = 0)
      accountSigned <- account.sign(kp.getPrivate)(cripto).rethrow
      publicKeyAccount = accountSigned.message.address.publicKey.toArray.toVector
      publicKey   <- cripto.publicKey[IO](publicKeyAccount).rethrow
      validateRes <- accountSigned.validate(publicKey)(cripto).rethrow
    } yield validateRes

    assertIOBoolean(r)

  }

}
