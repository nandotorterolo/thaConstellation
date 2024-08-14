package io.github.nandotorterolo.validators

import java.security.Security

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models._
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.node.validator.TransactionsMultipleOverdraftValidatorImpl
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider

class TransactionsMultipleOverdraftValidatorSpec extends CatsEffectSuite {
  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  /**
   * Overdraft - a valid signed transaction is rejected if the transaction amount exceeds the balance of the source wallet.
   * In this case, is for all the transaction in the mempool
   *
   * account A: balance 10
   * List(tx_1_A->B : 5, tx_2_A->C : 6) -> total = 11 > 10
   */
  test("Invalid: Transactions should be valid if multiple transactions Overdraft") {

    val res = for {

      kpA <- cripto.getKeyPair.rethrow
      kpB <- cripto.getKeyPair.rethrow

      address_A = Address(kpA.getPublic)
      address_B = Address(kpB.getPublic)

      account_A = Account(address_A, balance = 10d, latestUsedNonce = 0)
      account_B = Account(address_B, balance = 0d, latestUsedNonce = 0)

      transaction_1 = Transaction(address_A.addressId, address_B.addressId, 5, 1)
      // transfer error 6 should be 5, to be valid
      transaction_2 = Transaction(address_A.addressId, address_B.addressId, 6, 2)

      transactionSigned_1 <- EitherT(transaction_1.sign(kpA.getPrivate)(cripto)).rethrowT

      transactionSigned_2 <- EitherT(transaction_2.sign(kpA.getPrivate)(cripto)).rethrowT

      accountsMap = Map(
        account_A.address.addressId -> account_A,
        account_B.address.addressId -> account_B,
      )

      validator = TransactionsMultipleOverdraftValidatorImpl.build[IO](storageMock(accountsMap))

      transactions = Vector(transactionSigned_1.message, transactionSigned_2.message)

      res <- validator.validate(transactions)
    } yield res

    assertIO(res, (Message("Overdraft"): ModelThrowable).asLeft[Unit])
  }

  /**
   * Overdraft - a valid signed transaction is rejected if the transaction amount exceeds the balance of the source wallet.
   * In this case, is for all the transaction in the mempool
   *
   * account A: balance 10
   * List(tx_1_A->B : 5, tx_2_A->C : 5) -> total = 10 > 10
   */
  test("Valid: Transactions should be valid if multiple transactions Overdraft") {

    val res = for {

      kpA <- cripto.getKeyPair.rethrow
      kpB <- cripto.getKeyPair.rethrow

      address_A = Address(kpA.getPublic)
      address_B = Address(kpB.getPublic)

      account_A = Account(address_A, balance = 10d, latestUsedNonce = 0)
      account_B = Account(address_B, balance = 0d, latestUsedNonce = 0)

      transaction_1 = Transaction(address_A.addressId, address_B.addressId, 5, 1)
      // transfer error 6 should be 5, to be valid
      transaction_2 = Transaction(address_A.addressId, address_B.addressId, 5, 2)

      transactionSigned_1 <- EitherT(transaction_1.sign(kpA.getPrivate)(cripto)).rethrowT
      transactionSigned_2 <- EitherT(transaction_2.sign(kpA.getPrivate)(cripto)).rethrowT

      accountsMap = Map(
        account_A.address.addressId -> account_A,
        account_B.address.addressId -> account_B,
      )

      validator = TransactionsMultipleOverdraftValidatorImpl.build[IO](storageMock(accountsMap))

      transactions = Vector(transactionSigned_1.message, transactionSigned_2.message)

      res <- validator.validate(transactions)
    } yield res

    assertIO(res, ().asRight[ModelThrowable])
  }

}
