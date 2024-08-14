package io.github.nandotorterolo.validators

import java.security.Security

import cats.effect.IO
import cats.implicits._
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models._
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.node.validator.TransactionNonceValidatorImpl
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider

class TransactionNonceValidatorSpec extends CatsEffectSuite {

  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  test("Nonce Validator should success. 0 < 1") {

    val res = for {

      kpSource      <- cripto.getKeyPair.rethrow
      kpDestination <- cripto.getKeyPair.rethrow

      sourceAddress = Address(kpSource.getPublic)
      destAddress   = Address(kpDestination.getPublic)
      transaction   = Transaction(sourceAddress.addressId, destAddress.addressId, 100, nonce = 1)

      account = Account(sourceAddress, balance = 100d, latestUsedNonce = 0)

      accountsMap = Map(sourceAddress.addressId -> account)
      validator   = TransactionNonceValidatorImpl.build[IO](storageMock(accountsMap))
      res <- validator.validate(transaction)
    } yield res

    assertIO(res, ().asRight[ModelThrowable])
  }

  test("Nonce Validator should success with diff n, 0 < 100") {

    val res = for {

      kpSource      <- cripto.getKeyPair.rethrow
      kpDestination <- cripto.getKeyPair.rethrow

      sourceAddress = Address(kpSource.getPublic)
      destAddress   = Address(kpDestination.getPublic)
      transaction   = Transaction(sourceAddress.addressId, destAddress.addressId, 100, nonce = 100)

      account = Account(sourceAddress, balance = 100d, latestUsedNonce = 0)

      accountsMap = Map(sourceAddress.addressId -> account)

      validator = TransactionNonceValidatorImpl.build[IO](storageMock(accountsMap))
      res <- validator.validate(transaction)
    } yield res

    assertIO(res, ().asRight[ModelThrowable])
  }

  test("Nonce Validator should fail 0 < 0") {

    val res = for {

      kpSource      <- cripto.getKeyPair.rethrow
      kpDestination <- cripto.getKeyPair.rethrow

      sourceAddress = Address(kpSource.getPublic)
      destAddress   = Address(kpDestination.getPublic)

      account = Account(sourceAddress, balance = 100, latestUsedNonce = 0)

      transaction = Transaction(sourceAddress.addressId, destAddress.addressId, 100, nonce = 0)

      accountsMap = Map(sourceAddress.addressId -> account)
      validator   = TransactionNonceValidatorImpl.build[IO](storageMock(accountsMap))
      res <- validator.validate(transaction)
    } yield res

    assertIO(res, (Message("Repeated transaction"): ModelThrowable).asLeft[Unit])
  }

  test("Nonce Validator should fail: 1 < 0") {

    val res = for {

      kpSource      <- cripto.getKeyPair.rethrow
      kpDestination <- cripto.getKeyPair.rethrow

      sourceAddress = Address(kpSource.getPublic)
      destAddress   = Address(kpDestination.getPublic)

      account     = Account(sourceAddress, balance = 100, latestUsedNonce = 1)
      transaction = Transaction(sourceAddress.addressId, destAddress.addressId, 100, nonce = 0)

      accountsMap = Map(sourceAddress.addressId -> account)
      validator   = TransactionNonceValidatorImpl.build[IO](storageMock(accountsMap))
      res <- validator.validate(transaction)
    } yield res

    assertIO(res, (Message("Repeated transaction"): ModelThrowable).asLeft[Unit])
  }

}
