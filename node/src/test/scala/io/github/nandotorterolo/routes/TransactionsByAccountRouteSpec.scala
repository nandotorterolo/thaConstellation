package io.github.nandotorterolo.routes

import java.security.Security

import cats.data.EitherT
import cats.effect.IO
import fs2.Chunk
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models._
import io.github.nandotorterolo.node.routes.TransactionsByAccountRoute
import io.github.nandotorterolo.validators.storageMock
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.http4s._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import scodec.Codec

class TransactionsByAccountRouteSpec extends CatsEffectSuite {

  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  test("Transaction by account status code 200") {

    for {
      kpSource      <- cripto.getKeyPair.rethrow
      kpDestination <- cripto.getKeyPair.rethrow
      addressSource      = Address(kpSource.getPublic)
      addressDestination = Address(kpDestination.getPublic)

      accountSource = Account(address = addressSource, balance = 0d, latestUsedNonce = 0)

      transaction_A = Transaction(addressSource.addressId, addressDestination.addressId, 1, 1)
      transactionSigned_A <- EitherT(transaction_A.sign(kpSource.getPrivate)(cripto)).rethrowT

      transaction_B = Transaction(addressDestination.addressId, addressSource.addressId, 1, 1)
      transactionSigned_B <- EitherT(transaction_B.sign(kpSource.getPrivate)(cripto)).rethrowT

      addressIdSourceSigned <- EitherT(addressSource.addressId.sign(kpSource.getPrivate)(cripto)).rethrowT

      request: Request[IO] = {
        val chunk = Chunk.array(Codec[AddressIdSigned].encode(addressIdSourceSigned).require.bytes.toArray)

        Request[IO]()
          .withUri(uri"/account/transactions")
          .withMethod(Method.POST)
          .withContentType(`Content-Type`(MediaType.application.`octet-stream`))
          .withBodyStream(fs2.Stream.chunk(chunk).covary[IO])
      }

      res = TransactionsByAccountRoute
        .route(
          cripto,
          storageMock(
            accounts = Map(addressSource.addressId -> accountSource),
            transactions = Map(
              TransactionId(transactionSigned_A.hash.value) -> transactionSigned_A,
              TransactionId(transactionSigned_B.hash.value) -> transactionSigned_B
            )
          )
        )
        .orNotFound(request)

      _ <- assertIO(res.map(_.status), Status.Ok)

    } yield ()
  }

  test("Transaction by account status code 403") {

    for {
      kpSource      <- cripto.getKeyPair.rethrow
      kpDestination <- cripto.getKeyPair.rethrow
      addressSource      = Address(kpSource.getPublic)
      addressDestination = Address(kpDestination.getPublic)

      accountSource = Account(address = addressSource, balance = 0d, latestUsedNonce = 0)

      transaction_A = Transaction(addressSource.addressId, addressDestination.addressId, 1, 1)
      transactionSigned_A <- EitherT(transaction_A.sign(kpSource.getPrivate)(cripto)).rethrowT

      transaction_B = Transaction(addressDestination.addressId, addressSource.addressId, 1, 1)
      transactionSigned_B <- EitherT(transaction_B.sign(kpSource.getPrivate)(cripto)).rethrowT

      // sign the request with wrong private key
      addressIdSourceSigned <- EitherT(addressSource.addressId.sign(kpDestination.getPrivate)(cripto)).rethrowT

      request: Request[IO] = {
        val chunk = Chunk.array(Codec[AddressIdSigned].encode(addressIdSourceSigned).require.bytes.toArray)

        Request[IO]()
          .withUri(uri"/account/transactions")
          .withMethod(Method.POST)
          .withContentType(`Content-Type`(MediaType.application.`octet-stream`))
          .withBodyStream(fs2.Stream.chunk(chunk).covary[IO])
      }

      res = TransactionsByAccountRoute
        .route(
          cripto,
          storageMock(
            accounts = Map(addressSource.addressId -> accountSource),
            transactions = Map(
              TransactionId(transactionSigned_A.hash.value) -> transactionSigned_A,
              TransactionId(transactionSigned_B.hash.value) -> transactionSigned_B
            )
          )
        )
        .orNotFound(request)

      _ <- assertIO(res.map(_.status), Status.Forbidden)

    } yield ()
  }

}
