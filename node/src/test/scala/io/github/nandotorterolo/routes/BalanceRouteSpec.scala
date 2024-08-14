package io.github.nandotorterolo.routes

import java.security.Security

import cats.data.EitherT
import cats.effect.IO
import cats.implicits.showInterpolator
import fs2.Chunk
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models.Account
import io.github.nandotorterolo.models.Address
import io.github.nandotorterolo.node.routes.BalanceRoute
import io.github.nandotorterolo.validators.storageMock
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.http4s._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._

class BalanceRouteSpec extends CatsEffectSuite {

  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  private def createRequest(chunk: Chunk[Byte]): Request[IO] = {
    Request[IO]()
      .withUri(uri"/account/balance")
      .withMethod(Method.POST)
      .withContentType(`Content-Type`(MediaType.application.`octet-stream`))
      .withBodyStream(fs2.Stream.chunk(chunk).covary[IO])
  }

  test("BalanceRouteSpec returns status code 200") {

    for {
      kp <- cripto.getKeyPair.rethrow

      address   = Address(kp.getPublic)
      addressId = address.addressId
      account   = Account(address = address, balance = 0d, latestUsedNonce = 0)
      addressIdSigned <- EitherT(addressId.sign(kp.getPrivate)(cripto)).rethrowT
      request = createRequest(Chunk.array(addressIdSigned.encode.require.bytes.toArray))
      res     = BalanceRoute.route(cripto, storageMock(Map(addressId -> account))).orNotFound(request)
      _ <- assertIO(res.map(_.status), Status.Ok)
      _ <- assertIO(res.flatMap(_.as[String]), show"""{"address":"$addressId","balance":0.0,"latestUsedNonce":0}""".stripMargin)
    } yield ()

  }

  test("BalanceRouteSpec returns status code 404") {

    for {
      kp <- cripto.getKeyPair.rethrow

      address   = Address(kp.getPublic)
      addressId = address.addressId
      addressIdSigned <- EitherT(addressId.sign(kp.getPrivate)(cripto)).rethrowT
      request = createRequest(Chunk.array(addressIdSigned.encode.require.bytes.toArray))
      res     = BalanceRoute.route(cripto, storageMock()).orNotFound(request)
      _ <- assertIO(res.map(_.status), Status.NotFound)
      _ <- assertIO(res.flatMap(_.as[String]), show""""Entity not found"""".stripMargin)
    } yield ()

  }

  test("BalanceRouteSpec returns 404, invalid signature") {

    for {
      kp       <- cripto.getKeyPair.rethrow
      kp_Other <- cripto.getKeyPair.rethrow

      address   = Address(kp.getPublic)
      addressId = address.addressId
      account   = Account(address = address, balance = 0d, latestUsedNonce = 0)
      addressIdSigned <- EitherT(addressId.sign(kp_Other.getPrivate)(cripto)).rethrowT
      request = createRequest(Chunk.array(addressIdSigned.encode.require.bytes.toArray))

      res = BalanceRoute.route(cripto, storageMock(accounts = Map(addressId -> account))).orNotFound(request)
      _ <- assertIO(res.map(_.status), Status.BadRequest)
      _ <- assertIO(res.flatMap(_.as[String]), show""""Signature Validation error"""".stripMargin)
    } yield ()

  }

}
