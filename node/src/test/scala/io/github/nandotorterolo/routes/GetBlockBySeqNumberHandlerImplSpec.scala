package io.github.nandotorterolo.routes

import java.security.Security

import cats.data.EitherT
import cats.effect.IO
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models.Block
import io.github.nandotorterolo.models.BlockId
import io.github.nandotorterolo.node.routes.GetBlockBySeqNumberHandlerImpl
import io.github.nandotorterolo.server.autogenerated.block.BlockResource
import io.github.nandotorterolo.validators.storageMock
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.http4s._
import org.http4s.implicits._
import scodec.bits.ByteVector

class GetBlockBySeqNumberHandlerImplSpec extends CatsEffectSuite {

  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  test("Get Block by Seq Number returns status code 200") {

    for {
      kp <- cripto.getKeyPair.rethrow

      block = Block(BlockId(ByteVector.fill(20)(0)), 0, Vector.empty)
      blockSigned <- EitherT(block.sign(kp.getPrivate)(cripto)).rethrowT
      blockId = BlockId(blockSigned.hash.value)

      request: Request[IO] = {
        Request[IO]()
          .withUri(uri"/block/0")
          .withMethod(Method.GET)
      }

      res = new BlockResource[IO]().routes(new GetBlockBySeqNumberHandlerImpl(storageMock(blocks = Map(blockId -> blockSigned)))).orNotFound(request)

      _ <- assertIO(res.map(_.status), Status.Ok)
      _ <- assertIO(
        res.flatMap(_.as[String]),
        """{"blockId":"bU8DzQ1Q3jfsKmnH5zL7A9RshQZptxggYrW6umskwFtDyQHnnUAw9xym1oWcY27nPCFkhBYz7beABrmV41qvxdt","priorBlock":"11111111111111111111","sequenceNumber":0,"transactions":""}""".stripMargin
      )
    } yield ()

  }

}
