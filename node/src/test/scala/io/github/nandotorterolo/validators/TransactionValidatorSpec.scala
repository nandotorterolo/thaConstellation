package io.github.nandotorterolo.validators

import java.security.Security

import cats.data.NonEmptyChain
import cats.effect.IO
import cats.implicits._
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models.Address
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.models.Transaction
import io.github.nandotorterolo.node.validator.TransactionValidator
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider

class TransactionValidatorSpec extends CatsEffectSuite {

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }
  private val validator  = TransactionValidator.build[IO]
  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  private def createTx(amount: Double): IO[Transaction] = {
    for {
      kp     <- cripto.getKeyPair.rethrow
      kpDest <- cripto.getKeyPair.rethrow
      transaction = Transaction(
        source = Address(kp.getPublic).addressId,
        destination = Address(kpDest.getPublic).addressId,
        amount = amount,
        nonce = 0
      )
    } yield transaction
  }

  test("valid Transaction") {
    val res = createTx(amount = 1d).flatMap(validator.validateChain)
    assertIOBoolean(res.map(_.isRight))

  }

  test("amountValidation") {
    val res      = createTx(amount = 0d).flatMap(validator.validateChain)
    val expected = NonEmptyChain(Message("Non Positive Amount(0.0)")).asLeft[Transaction]
    assertIO(res, expected)
  }

  test("SourceDestinationAddressValidation") {
    def createTxSameAddress(): IO[Transaction] = {
      for {
        kp <- cripto.getKeyPair.rethrow
        transaction = Transaction(
          source = Address(kp.getPublic).addressId,
          destination = Address(kp.getPublic).addressId,
          amount = 1,
          nonce = 0
        )

      } yield transaction
    }

    val res      = createTxSameAddress().flatMap(validator.validateChain)
    val expected = NonEmptyChain(Message("Source and Destination Address are the same")).asLeft[Transaction]
    assertIO(res, expected)
  }

}
