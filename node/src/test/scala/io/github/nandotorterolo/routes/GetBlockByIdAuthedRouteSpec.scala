package io.github.nandotorterolo.routes

import java.security.Security

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models.Account
import io.github.nandotorterolo.models.Address
import io.github.nandotorterolo.models.Block
import io.github.nandotorterolo.models.BlockId
import io.github.nandotorterolo.node.routes.AuthenticationMiddleware
import io.github.nandotorterolo.node.routes.GetBlockByIdAuthedRoute
import io.github.nandotorterolo.validators.storageMock
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.http4s._
import org.http4s.headers.Authorization
import org.http4s.implicits._
import scodec.bits.ByteVector

class GetBlockByIdAuthedRouteSpec extends CatsEffectSuite {

  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  test("Get Block by Id returns status code 200, auth wrapper is mock") {

    for {
      kp <- cripto.getKeyPair.rethrow

      address = Address(kp.getPublic)
      account = Account(address, 0, 0)

      block = Block(BlockId(ByteVector.fill(20)(0)), 0, Vector.empty)
      blockSigned <- EitherT(block.sign(kp.getPrivate)(cripto)).rethrowT
      blockId = BlockId(blockSigned.hash.value)

      request: Request[IO] =
        Request[IO]()
          .withMethod(Method.GET)
          .withUri(uri"/block" / blockId.value.toBase58)

      authedRequest <- AuthedRequest[IO, Account]((_: Request[IO]) => account.pure[IO]).apply(request)

      res <- GetBlockByIdAuthedRoute
        .route(storageMock(blocks = Map(blockId -> blockSigned)))
        .orNotFound(authedRequest)

      _ = assertEquals(res.status, Status.Ok)
      _ <- assertIOBoolean(res.as[String].map(_.contains("sequenceNumber\":0")))

    } yield ()

  }

  test("Get Block by Id returns status code 403 using auth middleware") {

    for {
      kp <- cripto.getKeyPair.rethrow

      block = Block(BlockId(ByteVector.fill(20)(0)), 0, Vector.empty)
      blockSigned <- EitherT(block.sign(kp.getPrivate)(cripto)).rethrowT
      blockId = BlockId(blockSigned.hash.value)

      request: Request[IO] =
        Request[IO]()
          .withMethod(Method.GET)
          .withUri(uri"/block" / blockId.value.toBase58)

      storageMockImpl = storageMock(blocks = Map(blockId -> blockSigned))

      authMiddleWare = AuthenticationMiddleware[IO](cripto, storageMockImpl)

      res <- authMiddleWare.authMiddleware(GetBlockByIdAuthedRoute.route(storageMockImpl)).orNotFound(request)
      _ = assertEquals(res.status, Status.Forbidden)
      _ <- assertIO(res.as[String], "Couldn't find an Authorization header")

    } yield ()

  }

  test("Get Block by Id returns status code 403 using auth middleware, with wrong auth header") {

    for {
      kp <- cripto.getKeyPair.rethrow

      block = Block(BlockId(ByteVector.fill(20)(0)), 0, Vector.empty)
      blockSigned <- EitherT(block.sign(kp.getPrivate)(cripto)).rethrowT
      blockId = BlockId(blockSigned.hash.value)

      request: Request[IO] =
        Request[IO]()
          .withUri(uri"/block" / blockId.value.toBase58)
          .withMethod(Method.GET)
          .withHeaders(Headers(Authorization(BasicCredentials("username", "password"))))

      storageMockImpl = storageMock(blocks = Map(blockId -> blockSigned))

      authMiddleWare = AuthenticationMiddleware[IO](cripto, storageMockImpl)

      res <- authMiddleWare.authMiddleware(GetBlockByIdAuthedRoute.route(storageMockImpl)).orNotFound(request)

      _ = assertEquals(res.status, Status.Forbidden)
      _ <- assertIO(res.as[String], "Couldn't create Address Id")

    } yield ()

  }

  test("Get Block by Id returns status code 200 using auth middleware, with good auth header") {

    for {
      kp <- cripto.getKeyPair.rethrow

      address = Address(kp.getPublic)
      account = Account(address = address, balance = 0, latestUsedNonce = 0)
      addressIdSigned <- account.address.addressId.sign[IO](kp.getPrivate)(cripto).rethrow

      block = Block(BlockId(ByteVector.fill(20)(0)), 0, Vector.empty)
      blockSigned <- EitherT(block.sign(kp.getPrivate)(cripto)).rethrowT
      blockId = BlockId(blockSigned.hash.value)

      request: Request[IO] = Request[IO]()
        .withMethod(Method.GET)
        .withUri(uri"/block" / blockId.value.toBase58)
        .withHeaders(
          Headers(
            Authorization(
              BasicCredentials(
                addressIdSigned.message.value.toBase58,
                addressIdSigned.encodeToB58.require
              )
            )
          )
        )

      storageMockImpl = storageMock(
        accounts = Map(address.addressId -> account),
        blocks = Map(blockId -> blockSigned)
      )

      authMiddleWare = AuthenticationMiddleware[IO](cripto, storageMockImpl)

      res <- authMiddleWare.authMiddleware(GetBlockByIdAuthedRoute.route(storageMockImpl)).orNotFound(request)

      _ = assertEquals(res.status, Status.Ok)
      _ <- assertIOBoolean(res.as[String].map(_.contains("sequenceNumber\":0")))

    } yield ()

  }

  test("Get Block by Id returns status code 200 using auth middleware, with good auth header, not found") {

    for {
      kp <- cripto.getKeyPair.rethrow

      address = Address(kp.getPublic)
      account = Account(address = address, balance = 0, latestUsedNonce = 0)
      addressIdSigned <- account.address.addressId.sign[IO](kp.getPrivate)(cripto).rethrow

      block = Block(BlockId(ByteVector.fill(20)(0)), 0, Vector.empty)
      blockSigned <- EitherT(block.sign(kp.getPrivate)(cripto)).rethrowT
      blockId = BlockId(blockSigned.hash.value)

      request: Request[IO] = Request[IO]()
        .withMethod(Method.GET)
        .withUri(uri"/block" / blockId.value.toBase58)
        .withHeaders(
          Headers(
            Authorization(
              BasicCredentials(
                addressIdSigned.message.value.toBase58,
                addressIdSigned.encodeToB58.require
              )
            )
          )
        )

      storageMockImpl = storageMock(
        accounts = Map(address.addressId -> account)
      )

      authMiddleWare = AuthenticationMiddleware[IO](cripto, storageMockImpl)

      res <- authMiddleWare.authMiddleware(GetBlockByIdAuthedRoute.route(storageMockImpl)).orNotFound(request)

      _ = assertEquals(res.status, Status.NotFound)
      _ <- assertIO(res.as[String], "\"Block not found\"")

    } yield ()

  }

}
