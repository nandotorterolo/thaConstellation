package io.github.nandotorterolo.validators

import java.security.Security

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models._
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.node.validator.TransactionsMultipleNonceValidatorImpl
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider

class TransactionsMultipleNonceValidatorSpec extends CatsEffectSuite {
  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  test("Invalid: Transactions should be valid if multiple transactions repeated") {

    val res = for {

      kpA <- cripto.getKeyPair.rethrow
      kpB <- cripto.getKeyPair.rethrow

      address_A = Address(kpA.getPublic)
      address_B = Address(kpB.getPublic)

      account_A = Account(address_A, balance = 10d, latestUsedNonce = 0)
      account_B = Account(address_B, balance = 0d, latestUsedNonce = 0)

      // repeated nonces
      transaction_1 = Transaction(address_A.addressId, address_B.addressId, 5, nonce = 1)
      transaction_2 = Transaction(address_A.addressId, address_B.addressId, 5, nonce = 1)

      transactionSigned_1 <- EitherT(transaction_1.sign(kpA.getPrivate)(cripto)).rethrowT

      transactionSigned_2 <- EitherT(transaction_2.sign(kpA.getPrivate)(cripto)).rethrowT

      accountsMap = Map(
        account_A.address.addressId -> account_A,
        account_B.address.addressId -> account_B,
      )

      validator = TransactionsMultipleNonceValidatorImpl.build[IO](storageMock(accountsMap))

      transactions = Vector(transactionSigned_1.message, transactionSigned_2.message)

      res <- validator.validate(transactions)
    } yield res

    assertIO(res, (Message("Repeated transaction"): ModelThrowable).asLeft[Unit])
  }

  test("Invalid: Transactions should be valid if multiple transactions repeated, used in last tx") {

    val res = for {

      kpA <- cripto.getKeyPair.rethrow
      kpB <- cripto.getKeyPair.rethrow

      address_A = Address(kpA.getPublic)
      address_B = Address(kpB.getPublic)

      account_A = Account(address_A, balance = 10d, latestUsedNonce = 0)
      account_B = Account(address_B, balance = 0d, latestUsedNonce = 0)

      // no repeated nonce, but 0 is used in last transaction
      transaction_1 = Transaction(address_A.addressId, address_B.addressId, 5, nonce = 0)
      transaction_2 = Transaction(address_A.addressId, address_B.addressId, 5, nonce = 1)

      transactionSigned_1 <- EitherT(transaction_1.sign(kpA.getPrivate)(cripto)).rethrowT

      transactionSigned_2 <- EitherT(transaction_2.sign(kpA.getPrivate)(cripto)).rethrowT

      accountsMap = Map(
        account_A.address.addressId -> account_A,
        account_B.address.addressId -> account_B,
      )

      validator = TransactionsMultipleNonceValidatorImpl.build[IO](storageMock(accountsMap))

      transactions = Vector(transactionSigned_1.message, transactionSigned_2.message)

      res <- validator.validate(transactions)
    } yield res

    assertIO(res, (Message("Repeated transaction"): ModelThrowable).asLeft[Unit])
  }

  test("Invalid: Transactions should be valid if multiple transactions repeated, not in range") {

    val res = for {

      kpA <- cripto.getKeyPair.rethrow
      kpB <- cripto.getKeyPair.rethrow

      address_A = Address(kpA.getPublic)
      address_B = Address(kpB.getPublic)

      account_A = Account(address_A, balance = 10d, latestUsedNonce = 0)
      account_B = Account(address_B, balance = 0d, latestUsedNonce = 0)

      // no repeated nonce,but 2 is missing in range
      transaction_1 = Transaction(address_A.addressId, address_B.addressId, 5, nonce = 1)
      transaction_2 = Transaction(address_A.addressId, address_B.addressId, 5, nonce = 3)

      transactionSigned_1 <- EitherT(transaction_1.sign(kpA.getPrivate)(cripto)).rethrowT

      transactionSigned_2 <- EitherT(transaction_2.sign(kpA.getPrivate)(cripto)).rethrowT

      accountsMap = Map(
        account_A.address.addressId -> account_A,
        account_B.address.addressId -> account_B,
      )

      validator = TransactionsMultipleNonceValidatorImpl.build[IO](storageMock(accountsMap))

      transactions = Vector(transactionSigned_1.message, transactionSigned_2.message)

      res <- validator.validate(transactions)
    } yield res

    assertIO(res, (Message("Repeated transaction"): ModelThrowable).asLeft[Unit])
  }

  test("Valid: Transactions should be valid if multiple transactions are not repeated") {

    val res = for {

      kpA <- cripto.getKeyPair.rethrow
      kpB <- cripto.getKeyPair.rethrow

      address_A = Address(kpA.getPublic)
      address_B = Address(kpB.getPublic)

      account_A = Account(address_A, balance = 10d, latestUsedNonce = 0)
      account_B = Account(address_B, balance = 0d, latestUsedNonce = 0)

      transaction_1 = Transaction(address_A.addressId, address_B.addressId, 5, nonce = 1)
      transaction_2 = Transaction(address_A.addressId, address_B.addressId, 5, nonce = 2)

      transactionSigned_1 <- EitherT(transaction_1.sign(kpA.getPrivate)(cripto)).rethrowT

      transactionSigned_2 <- EitherT(transaction_2.sign(kpA.getPrivate)(cripto)).rethrowT

      accountsMap = Map(
        account_A.address.addressId -> account_A,
        account_B.address.addressId -> account_B,
      )

      validator = TransactionsMultipleNonceValidatorImpl.build[IO](storageMock(accountsMap))

      transactions = Vector(transactionSigned_1.message, transactionSigned_2.message)

      res <- validator.validate(transactions)
    } yield res

    assertIO(res, ().asRight[ModelThrowable])
  }

}
