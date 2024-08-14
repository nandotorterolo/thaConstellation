package io.github.nandotorterolo.services

import java.security.Security

import cats.effect.IO
import cats.implicits._
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models._
import io.github.nandotorterolo.node.service.AccountsUpdaterImpl
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider

class AccountsUpdaterImplSpec extends CatsEffectSuite {
  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  test("A. Valid: update result should be expected") {

    for {

      kpA <- cripto.getKeyPair.rethrow
      kpB <- cripto.getKeyPair.rethrow

      address_A = Address(kpA.getPublic)
      address_B = Address(kpB.getPublic)

      account_A = Account(address_A, balance = 10d, latestUsedNonce = 0)
      account_B = Account(address_B, balance = 10d, latestUsedNonce = 0)

      // A -> B  total ammount = 9
      transaction_1 = Transaction(address_A.addressId, address_B.addressId, 5, 1)
      transaction_2 = Transaction(address_A.addressId, address_B.addressId, 4, 2)

      // B -> A total ammount = 10
      transaction_3 = Transaction(address_B.addressId, address_A.addressId, 5, 1)
      transaction_4 = Transaction(address_B.addressId, address_A.addressId, 5, 2)

      transactions = Vector(transaction_1, transaction_2, transaction_3, transaction_4)
      expectedBalances = Map(
        account_A.address.addressId -> 1d,
        account_B.address.addressId -> -1d
      )

      expectedNonces = Map(
        account_A.address.addressId -> 2,
        account_B.address.addressId -> 2
      )

      _ <- assertIO(AccountsUpdaterImpl.balances(transactions).pure[IO], expectedBalances)
      _ <- assertIO(AccountsUpdaterImpl.nonces(transactions).pure[IO], expectedNonces)
    } yield ()

  }

  test("B. Valid: update result should be expected") {

    for {

      kpA <- cripto.getKeyPair.rethrow
      kpB <- cripto.getKeyPair.rethrow

      address_A = Address(kpA.getPublic)
      address_B = Address(kpB.getPublic)

      account_A = Account(address_A, balance = 10d, latestUsedNonce = 0)
      account_B = Account(address_B, balance = 10d, latestUsedNonce = 0)

      // A -> B  total ammount = 9
      transaction_1 = Transaction(address_A.addressId, address_B.addressId, 5, 1)
      transaction_2 = Transaction(address_A.addressId, address_B.addressId, 4, 2)

      // B -> A total ammount = 1
      transaction_3 = Transaction(address_B.addressId, address_A.addressId, 1, 1)

      transactions = Vector(transaction_1, transaction_2, transaction_3)
      expectedBalances = Map(
        account_A.address.addressId -> -8d,
        account_B.address.addressId -> 8d
      )

      expectedNonces = Map(
        account_A.address.addressId -> 2,
        account_B.address.addressId -> 1
      )

      _ <- assertIO(AccountsUpdaterImpl.balances(transactions).pure[IO], expectedBalances)
      _ <- assertIO(AccountsUpdaterImpl.nonces(transactions).pure[IO], expectedNonces)
    } yield ()

  }

  test("C. Valid: update result should be expected") {

    for {

      kpA <- cripto.getKeyPair.rethrow
      kpB <- cripto.getKeyPair.rethrow

      address_A = Address(kpA.getPublic)
      address_B = Address(kpB.getPublic)

      account_A = Account(address_A, balance = 10d, latestUsedNonce = 0)
      account_B = Account(address_B, balance = 10d, latestUsedNonce = 0)

      // A -> B  total ammount = 5
      transaction_1 = Transaction(address_A.addressId, address_B.addressId, 5, 1)

      // B -> A total ammount = 5
      transaction_2 = Transaction(address_B.addressId, address_A.addressId, 5, 1)

      transactions = Vector(transaction_1, transaction_2)
      expectedBalances = Map(
        account_A.address.addressId -> 0d,
        account_B.address.addressId -> 0d
      )

      expectedNonces = Map(
        account_A.address.addressId -> 1,
        account_B.address.addressId -> 1
      )

      _ <- assertIO(AccountsUpdaterImpl.balances(transactions).pure[IO], expectedBalances)
      _ <- assertIO(AccountsUpdaterImpl.nonces(transactions).pure[IO], expectedNonces)
    } yield ()

  }

  test("D. Valid: update result should be expected") {

    for {

      kpA <- cripto.getKeyPair.rethrow
      kpB <- cripto.getKeyPair.rethrow
      kpC <- cripto.getKeyPair.rethrow

      address_A = Address(kpA.getPublic)
      address_B = Address(kpB.getPublic)
      address_C = Address(kpC.getPublic)

      account_A = Account(address_A, balance = 10d, latestUsedNonce = 0)
      account_B = Account(address_B, balance = 10d, latestUsedNonce = 0)
      account_C = Account(address_C, balance = 10d, latestUsedNonce = 0)

      // A -> B  total ammount = 5
      transaction_1 = Transaction(address_A.addressId, address_B.addressId, 5, 1)

      // B -> C total ammount = 5
      transaction_2 = Transaction(address_B.addressId, address_C.addressId, 5, 1)

      // C -> A total ammount = 5
      transaction_3 = Transaction(address_C.addressId, address_A.addressId, 5, 1)

      transactions = Vector(transaction_1, transaction_2, transaction_3)
      expectedBalances = Map(
        account_A.address.addressId -> 0d,
        account_B.address.addressId -> 0d,
        account_C.address.addressId -> 0d
      )

      expectedNonces = Map(
        account_A.address.addressId -> 1,
        account_B.address.addressId -> 1,
        account_C.address.addressId -> 1
      )
      _ <- assertIO(AccountsUpdaterImpl.balances(transactions).pure[IO], expectedBalances)
      _ <- assertIO(AccountsUpdaterImpl.nonces(transactions).pure[IO], expectedNonces)
    } yield ()

  }

}
