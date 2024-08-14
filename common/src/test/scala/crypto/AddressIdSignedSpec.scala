package crypto

import java.security.Security

import cats.effect.IO
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models.AddressId
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider

class AddressIdSignedSpec extends CatsEffectSuite {

  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  test("Sign AddressId using Signable") {
    val r = for {
      kp <- cripto.getKeyPair.rethrow
      addressId = AddressId(kp.getPublic)
      addressIdSigned <- addressId.sign(kp.getPrivate)(cripto).rethrow
      publicKey       <- cripto.publicKey[IO](kp.getPublic.getEncoded.toVector).rethrow
      validateRes     <- addressIdSigned.validate(publicKey)(cripto).rethrow
    } yield validateRes

    assertIOBoolean(r)

  }

}
