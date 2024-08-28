package io.github.nandotorterolo.routes

import java.security.Security

import cats.effect.Async
import cats.effect.IO
import cats.implicits._
import fs2.Chunk
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signature
import io.github.nandotorterolo.models._
import io.github.nandotorterolo.node.interfaces.MemPoolService
import io.github.nandotorterolo.node.interfaces.StorageService
import io.github.nandotorterolo.node.routes.RegistrationRoute
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.http4s._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import org.http4s.Request
import scodec.bits.ByteVector
import scodec.Codec

class RegistrationRouteSpec extends CatsEffectSuite {

  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  def ss(accountSigned: AccountSigned): StorageService[IO] =
    new StorageService[IO] {

      override def getServerAccount: IO[Either[ModelThrowable, Account]] = ???

      override def createServerAccount(): IO[Either[ModelThrowable, AccountSigned]] = ???

      override def getAccount(accountId: AddressId): IO[Option[Account]] = ???

      override def saveBlock(block: BlockSigned, txs: Vector[TransactionSigned]): IO[Either[ModelThrowable, BlockSigned]] = ???

      override def getBlockHead: IO[Either[ModelThrowable, BlockSigned]] = ???

      override def getBlock(blockId: BlockId): IO[Either[ModelThrowable, BlockSigned]] = ???

      override def getBlockBySeqNumber(seqNumber: Int): IO[Either[ModelThrowable, BlockSigned]] = ???

      override def createGenesisBlock(): IO[Either[ModelThrowable, BlockSigned]] = ???

      override def saveAccount(account: AccountSigned): IO[Either[ModelThrowable, AccountSigned]] =
        accountSigned.asRight[ModelThrowable].pure[IO]

      override def getTransaction(transactionId: TransactionId): IO[Either[ModelThrowable, Transaction]] = ???

      override def getTransactionByAccount(accountId: AddressId): IO[Either[ModelThrowable, Vector[TransactionSigned]]] = ???

      override def crateGiftTransaction(
          newAccount: AccountSigned
      ): IO[Either[ModelThrowable, TransactionSigned]] = {
        // TODO integrate mockito or other mock framework
        val t = TransactionSigned(
          Hash(ByteVector.empty),
          Signature(ByteVector.empty),
          Transaction(
            AddressId("JpE3CyJtqsJ35cE6U1uq7RKXLAg"),
            AddressId("2qYgUZwiJQJsJzrusHHnssU5UvGD"),
            1,
            1
          )
        )
        t.asRight[ModelThrowable].pure[IO]
      }
    }

  private val pool = new MemPoolService[IO] {
    override def addTransaction(transaction: TransactionSigned): IO[Unit] = Async[IO].unit
    override def processListTxs(txs: Vector[TransactionSigned]): IO[Unit] = Async[IO].unit
  }

  def createRequest(chunk: Chunk[Byte]): Request[IO] =
    Request[IO]()
      .withUri(uri"/account/register")
      .withMethod(Method.POST)
      .withContentType(`Content-Type`(MediaType.application.`octet-stream`))
      .withBodyStream(fs2.Stream.chunk(chunk).covary[IO])

  test("RegisterRouteSpec returns status code 200") {

    for {
      kp <- cripto.getKeyPair.rethrow

      address = Address(kp.getPublic)
      account = Account(address, balance = 0d, latestUsedNonce = 0)

      accountSigned <- account.sign(kp.getPrivate)(cripto).rethrow

      chunk   = Chunk.array(Codec[AccountSigned].encode(accountSigned).require.bytes.toArray)
      request = createRequest(chunk)

      res = RegistrationRoute.route(cripto, ss(accountSigned), pool).orNotFound(request)

      _ <- assertIO(res.map(_.status), Status.Ok)
      _ <- assertIO(
        res.flatMap(_.as[String]),
        s"""{"account":{"address":"${address.addressId.value.toBase58}","balance":0.0,"latestUsedNonce":0}}""".stripMargin
      )
    } yield ()

  }

  test("RegisterRouteSpec returns status code 400") {

    for {
      kp       <- cripto.getKeyPair.rethrow
      kp_Other <- cripto.getKeyPair.rethrow

      address = Address(kp.getPublic)
      account = Account(address, balance = 0d, latestUsedNonce = 0)

      accountSigned <- account.sign(kp_Other.getPrivate)(cripto).rethrow

      chunk   = Chunk.array(Codec[AccountSigned].encode(accountSigned).require.bytes.toArray)
      request = createRequest(chunk)

      res = RegistrationRoute.route(cripto, ss(accountSigned), pool).orNotFound(request)

      _ <- assertIO(res.map(_.status), Status.BadRequest)
      _ <- assertIO(res.flatMap(_.as[String]), show""""Signature Validation error"""".stripMargin)
    } yield ()

  }

}
