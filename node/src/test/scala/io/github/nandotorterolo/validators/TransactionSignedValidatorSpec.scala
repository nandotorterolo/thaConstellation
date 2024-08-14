package io.github.nandotorterolo.validators

import java.security.Security

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models._
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.node.validator.TransactionSignedValidatorImpl
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider

class TransactionSignedValidatorSpec extends CatsEffectSuite {

  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]
  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  /**
   * Transaction signed by wrong wallet - a signed transaction signed by a different
   * wallet than the source wallet is rejected and does not result in a balance transfer.
   */
  test("SignedValidator should fail, A -> B, A wrong signature") {

    val res = for {

      kpA <- cripto.getKeyPair.rethrow
      kpB <- cripto.getKeyPair.rethrow

      address_A = Address(kpA.getPublic)
      address_B = Address(kpB.getPublic)

      account_A = Account(address_A, balance = 100d, latestUsedNonce = 0)
      account_B = Account(address_B, balance = 1d, latestUsedNonce = 0)

      transaction = Transaction(address_A.addressId, address_B.addressId, 100, 0)

      // the transaction is signed with B account
      transactionSigned <- EitherT(transaction.sign(kpB.getPrivate)(cripto)).rethrowT

      accountsMap = Map(
        account_A.address.addressId -> account_A,
        account_B.address.addressId -> account_B,
      )

      validator = TransactionSignedValidatorImpl.build[IO](cripto, storageMock(accountsMap))

      res <- validator.validate(transactionSigned)
    } yield res

    assertIO(res, (Message("SignatureValidation"): ModelThrowable).asLeft[Unit])
  }

  /**
   * Invalid signed transaction - a signed transaction with a totally invalid signature is
   * rejected and does not result in a balance transfer.
   */
  test("SignedValidator should fail, A -> B, a total invalid signature".fail) {

    val invalidPrivate_A =
      this.getClass.getClassLoader.getResourceAsStream("keys/invalid").readAllBytes()

    // in the second line of the file I did change 'A' -> 'B'
    // java.security.spec.InvalidKeySpecException: encoded key spec not recognized: failed to construct sequence from byte[]: Extra data detected in stream
    val invalidPublic_A =
      this.getClass.getClassLoader.getResourceAsStream("keys/invalid.pub").readAllBytes()

    val res = for {

      privateKey_Invalid_A <- cripto.privateKey[IO](invalidPrivate_A.toVector).rethrow
      publicKey_Invalid_A  <- cripto.publicKey[IO](invalidPublic_A.toVector).rethrow
      kpB                  <- cripto.getKeyPair.rethrow

      address_A = Address(publicKey_Invalid_A)
      address_B = Address(kpB.getPublic)

      account_A = Account(address_A, balance = 100d, latestUsedNonce = 0)
      account_B = Account(address_B, balance = 1d, latestUsedNonce = 0)

      transaction = Transaction(address_A.addressId, address_B.addressId, 100, 0)

      transactionSigned <- EitherT(transaction.sign(privateKey_Invalid_A)(cripto)).rethrowT

      accountsMap = Map(
        account_A.address.addressId -> account_A,
        account_B.address.addressId -> account_B,
      )

      validator = TransactionSignedValidatorImpl.build[IO](cripto, storageMock(accountsMap))

      res <- validator.validate(transactionSigned)
    } yield res

    assertIO(res, (Message("SignatureValidation"): ModelThrowable).asLeft[Unit])

  }

}
