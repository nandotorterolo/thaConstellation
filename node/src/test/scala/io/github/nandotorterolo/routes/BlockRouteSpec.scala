package io.github.nandotorterolo.routes

import java.security.Security

import cats.data.EitherT
import cats.effect.IO
import fs2.Chunk
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models.Block
import io.github.nandotorterolo.models.BlockId
import io.github.nandotorterolo.node.routes.BlockRoute
import io.github.nandotorterolo.validators.storageMock
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.http4s._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import scodec.bits.ByteVector
import scodec.Codec

class BlockRouteSpec extends CatsEffectSuite {

  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  test("BlockRouteSpec returns status code 200") {

    val response: IO[Response[IO]] = {

      for {
        kp <- cripto.getKeyPair.rethrow

        block = Block(BlockId(ByteVector.fill(20)(0)), 0, Vector.empty)
        blockSigned <- EitherT(block.sign(kp.getPrivate)(cripto)).rethrowT
        blockId = BlockId(blockSigned.hash.v)

        request: Request[IO] = {
          val chunk = Chunk.array(Codec[BlockId].encode(blockId).require.bytes.toArray)

          Request[IO]()
            .withUri(uri"/block")
            .withMethod(Method.POST)
            .withContentType(`Content-Type`(MediaType.application.`octet-stream`))
            .withBodyStream(fs2.Stream.chunk(chunk).covary[IO])
        }

        res <- BlockRoute.route(storageMock(blocks = Map(blockId -> block))).orNotFound(request)
      } yield res
    }

    assertIO(response.map(_.status), Status.Ok)
    assertIO(response.flatMap(_.as[String]), """"{\"priorBlock\":\"11111111111111111111\",\"sequenceNumber\":0,\"transactions\":\"\"}"""".stripMargin)
  }

}
