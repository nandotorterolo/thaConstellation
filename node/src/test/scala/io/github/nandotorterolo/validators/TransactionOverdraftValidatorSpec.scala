package io.github.nandotorterolo.validators

import java.security.Security

import cats.effect.IO
import cats.implicits._
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models._
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.node.validator.TransactionOverdraftValidatorImpl
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider

class TransactionOverdraftValidatorSpec extends CatsEffectSuite {

  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  test("OverdraftValidator should success") {

    val res = for {

      kpSource      <- cripto.getKeyPair.rethrow
      kpDestination <- cripto.getKeyPair.rethrow

      sourceAddress = Address(kpSource.getPublic)
      destAddress   = Address(kpDestination.getPublic)
      transaction   = Transaction(sourceAddress.addressId, destAddress.addressId, 100, 0)

      account = Account(sourceAddress, balance = 100d, latestUsedNonce = 0)

      accountsMap = Map(sourceAddress.addressId -> account)
      validator   = TransactionOverdraftValidatorImpl.build[IO](storageMock(accountsMap))
      res <- validator.validate(transaction)
    } yield res

    assertIO(res, ().asRight[ModelThrowable])
  }

  test("OverdraftValidator should fail") {

    val res = for {

      kpSource      <- cripto.getKeyPair.rethrow
      kpDestination <- cripto.getKeyPair.rethrow

      sourceAddress = Address(kpSource.getPublic)
      destAddress   = Address(kpDestination.getPublic)

      account = Account(sourceAddress, balance = 99, latestUsedNonce = 0) // 99< 100

      transaction = Transaction(sourceAddress.addressId, destAddress.addressId, 100, 0)

      accountsMap = Map(sourceAddress.addressId -> account)
      validator   = TransactionOverdraftValidatorImpl.build[IO](storageMock(accountsMap))
      res <- validator.validate(transaction)
    } yield res

    assertIO(res, (Message("Overdraft"): ModelThrowable).asLeft[Unit])
  }

}
