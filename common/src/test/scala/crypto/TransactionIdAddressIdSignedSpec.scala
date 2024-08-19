package crypto

import java.security.Security

import cats.data.EitherT
import cats.effect.IO
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models.Address
import io.github.nandotorterolo.models.Transaction
import io.github.nandotorterolo.models.TransactionId
import io.github.nandotorterolo.models.TransactionIdAddressId
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider

class TransactionIdAddressIdSignedSpec extends CatsEffectSuite {

  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  test("Sign Transaction") {
    val r = for {
      kpSource <- cripto.getKeyPair.rethrow

      publicKeySource = kpSource.getPublic
      addressSource   = Address(publicKeySource)

      kpDestination <- cripto.getKeyPair.rethrow
      addressDestination = Address(kpDestination.getPublic)

      transaction = Transaction(addressSource.addressId, addressDestination.addressId, amount = 100d, nonce = 0)
      transactionSigned <- EitherT(transaction.sign(kpSource.getPrivate)(cripto)).rethrowT
      transactionIdAddressId = TransactionIdAddressId(TransactionId(transactionSigned.hash.value), addressSource.addressId)
      transactionIdAddressIdSigned <- EitherT(transactionIdAddressId.sign(kpSource.getPrivate)(cripto)).rethrowT

      validateRes <- transactionIdAddressIdSigned.validate(publicKeySource)(cripto).rethrow

    } yield validateRes

    assertIOBoolean(r)

  }

}
