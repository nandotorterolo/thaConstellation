package io.github.nandotorterolo.services

import java.security.Security

import cats.data.EitherT
import cats.effect.IO
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models._
import io.github.nandotorterolo.node.service.TransactionsMultipleFilterImpl
import io.github.nandotorterolo.node.validator.TransactionsMultipleNonceValidatorImpl
import io.github.nandotorterolo.node.validator.TransactionsMultipleOverdraftValidatorImpl
import io.github.nandotorterolo.validators.storageMock
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider

class TransactionsMultipleFilterSpec extends CatsEffectSuite {
  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  /**
   * Multiple filter
   * T1, T2 valid
   * T3, T4 invalid
   */
  test("Valid: Filter of Transactions should discard all transaction from an account which multiple overdraft is invalid") {

    for {

      kpA <- cripto.getKeyPair.rethrow
      kpB <- cripto.getKeyPair.rethrow

      address_A = Address(kpA.getPublic)
      address_B = Address(kpB.getPublic)

      account_A = Account(address_A, balance = 10d, latestUsedNonce = 0)
      account_B = Account(address_B, balance = 10d, latestUsedNonce = 0)

      // Valid A->B sending 10 in 2 transactions
      transaction_1 = Transaction(address_A.addressId, address_B.addressId, 5, 1)
      transactionSigned_1 <- EitherT(transaction_1.sign(kpA.getPrivate)(cripto)).rethrowT

      transaction_2 = Transaction(address_A.addressId, address_B.addressId, 5, 2)
      transactionSigned_2 <- EitherT(transaction_2.sign(kpA.getPrivate)(cripto)).rethrowT

      // Invalid B->A sending 11 in 2 transactions, with balance  10, nonce incorrect

      transaction_3 = Transaction(address_B.addressId, address_A.addressId, 5, 1)
      transactionSigned_3 <- EitherT(transaction_3.sign(kpB.getPrivate)(cripto)).rethrowT

      transaction_4 = Transaction(address_B.addressId, address_A.addressId, 6, 2)
      transactionSigned_4 <- EitherT(transaction_4.sign(kpB.getPrivate)(cripto)).rethrowT

      accountsMap = Map(
        account_A.address.addressId -> account_A,
        account_B.address.addressId -> account_B,
      )

      tmNonceV     = TransactionsMultipleNonceValidatorImpl.build[IO](storageMock(accountsMap))
      tmOverdraftV = TransactionsMultipleOverdraftValidatorImpl.build[IO](storageMock(accountsMap))
      validator    = TransactionsMultipleFilterImpl.build[IO](tmNonceV, tmOverdraftV)

      transactions = Vector(transactionSigned_1, transactionSigned_2, transactionSigned_3, transactionSigned_4)

      res <- assertIO(validator.filterInvalids(transactions), Vector(transactionSigned_1, transactionSigned_2))
    } yield res

  }

  /**
   * Multiple filter
   * T1, T2 valid
   * T3, T4 invalid
   */
  test("Valid: Filter of Transactions should discard all transaction from an account which multiple nonce is invalid") {

    for {

      kpA <- cripto.getKeyPair.rethrow
      kpB <- cripto.getKeyPair.rethrow

      address_A = Address(kpA.getPublic)
      address_B = Address(kpB.getPublic)

      account_A = Account(address_A, balance = 10d, latestUsedNonce = 0)
      account_B = Account(address_B, balance = 10d, latestUsedNonce = 0)

      // Valid A->B sending 10 in 2 transactions
      transaction_1 = Transaction(address_A.addressId, address_B.addressId, 5, 1)
      transactionSigned_1 <- EitherT(transaction_1.sign(kpA.getPrivate)(cripto)).rethrowT

      transaction_2 = Transaction(address_A.addressId, address_B.addressId, 5, 2)
      transactionSigned_2 <- EitherT(transaction_2.sign(kpA.getPrivate)(cripto)).rethrowT

      // Invalid B->A sending 10 in 2 transactions, with balance  10, nonce incorrect
      transaction_3 = Transaction(address_B.addressId, address_A.addressId, 5, 1)
      transactionSigned_3 <- EitherT(transaction_3.sign(kpB.getPrivate)(cripto)).rethrowT

      transaction_4 = Transaction(address_B.addressId, address_A.addressId, 5, 1)
      transactionSigned_4 <- EitherT(transaction_4.sign(kpB.getPrivate)(cripto)).rethrowT

      accountsMap = Map(
        account_A.address.addressId -> account_A,
        account_B.address.addressId -> account_B,
      )

      tmNonceV     = TransactionsMultipleNonceValidatorImpl.build[IO](storageMock(accountsMap))
      tmOverdraftV = TransactionsMultipleOverdraftValidatorImpl.build[IO](storageMock(accountsMap))
      validator    = TransactionsMultipleFilterImpl.build[IO](tmNonceV, tmOverdraftV)

      transactions = Vector(transactionSigned_1, transactionSigned_2, transactionSigned_3, transactionSigned_4)

      res <- assertIO(validator.filterInvalids(transactions), Vector(transactionSigned_1, transactionSigned_2))
    } yield res

  }

}
