package io.github.nandotorterolo.routes

import java.security.Security

import cats.data.EitherT
import cats.effect.IO
import fs2.Chunk
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models._
import io.github.nandotorterolo.node.routes.TransactionInspectRoute
import io.github.nandotorterolo.validators.storageMock
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.http4s._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import scodec.Codec

class TransactionInspectRouteSpec extends CatsEffectSuite {

  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  test("Transaction inspect route returns status code 200") {

    for {
      kpSource      <- cripto.getKeyPair.rethrow
      kpDestination <- cripto.getKeyPair.rethrow
      addressSource      = Address(kpSource.getPublic)
      addressDestination = Address(kpDestination.getPublic)

      accountSource = Account(address = addressSource, balance = 0d, latestUsedNonce = 0)

      transaction = Transaction(addressSource.addressId, addressDestination.addressId, 1, 1)
      transactionSigned <- EitherT(transaction.sign(kpSource.getPrivate)(cripto)).rethrowT

      transactionIdAddressId = TransactionIdAddressId(TransactionId(transactionSigned.hash.value), addressSource.addressId)
      transactionIdAddressIdSigned <- EitherT(transactionIdAddressId.sign(kpSource.getPrivate)(cripto)).rethrowT

      request: Request[IO] = {
        val chunk = Chunk.array(Codec[TransactionIdAddressIdSigned].encode(transactionIdAddressIdSigned).require.bytes.toArray)

        Request[IO]()
          .withUri(uri"/transaction/inspect")
          .withMethod(Method.POST)
          .withContentType(`Content-Type`(MediaType.application.`octet-stream`))
          .withBodyStream(fs2.Stream.chunk(chunk).covary[IO])
      }

      res = TransactionInspectRoute
        .route(
          cripto,
          storageMock(
            accounts = Map(addressSource.addressId -> accountSource),
            transactions = Map(transactionIdAddressId.transactionId -> transaction)
          )
        )
        .orNotFound(request)

      _ <- assertIO(res.map(_.status), Status.Ok)
      _ <- assertIO(
        res.flatMap(_.as[String]),
        s""""{\\"source\\":\\"${addressSource.addressId.value.toBase58}\\",\\"destination\\":\\"${addressDestination.addressId.value.toBase58}\\",\\"amount\\":1.0,\\"nonce\\":1}"""".stripMargin
      )
    } yield ()
  }

  test("Transaction inspect route returns signature issued if the addres is not source or destination of the transaction") {

    for {
      kpSource      <- cripto.getKeyPair.rethrow
      kpDestination <- cripto.getKeyPair.rethrow
      kpOther       <- cripto.getKeyPair.rethrow

      addressSource      = Address(kpSource.getPublic)
      addressDestination = Address(kpDestination.getPublic)
      addressOther       = Address(kpOther.getPublic)

      accountOther = Account(address = addressOther, balance = 0d, latestUsedNonce = 0)

      transaction = Transaction(addressSource.addressId, addressDestination.addressId, 1, 1)
      transactionSigned <- EitherT(transaction.sign(kpSource.getPrivate)(cripto)).rethrowT

      // other account is asking for a transaction
      transactionIdAddressId = TransactionIdAddressId(TransactionId(transactionSigned.hash.value), addressOther.addressId)
      transactionIdAddressIdSigned <- EitherT(transactionIdAddressId.sign(kpOther.getPrivate)(cripto)).rethrowT

      request: Request[IO] = {
        val chunk = Chunk.array(Codec[TransactionIdAddressIdSigned].encode(transactionIdAddressIdSigned).require.bytes.toArray)

        Request[IO]()
          .withUri(uri"/transaction/inspect")
          .withMethod(Method.POST)
          .withContentType(`Content-Type`(MediaType.application.`octet-stream`))
          .withBodyStream(fs2.Stream.chunk(chunk).covary[IO])
      }

      res = TransactionInspectRoute
        .route(
          cripto,
          storageMock(
            accounts = Map(addressOther.addressId -> accountOther),
            transactions = Map(transactionIdAddressId.transactionId -> transaction)
          )
        )
        .orNotFound(request)

      _ <- assertIO(res.map(_.status), Status.Forbidden)
    } yield ()
  }

}
