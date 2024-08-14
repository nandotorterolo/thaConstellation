package crypto

import java.security.Security

import cats.effect.IO
import io.github.nandotorterolo.crypto.Cripto
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models.Block
import io.github.nandotorterolo.models.BlockId
import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider
import scodec.bits.ByteVector

class BlockSignedSpec extends CatsEffectSuite {

  val cripto: Cripto[IO] = EcdsaBCEncryption.build[IO]

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  test("Sign Block using Signable") {
    val r = for {
      kp <- cripto.getKeyPair.rethrow
      block = Block(BlockId(ByteVector.empty), 0, Vector.empty)
      blockSigned <- block.sign(kp.getPrivate)(cripto).rethrow
      publicKey   <- cripto.publicKey[IO](kp.getPublic.getEncoded.toVector).rethrow
      validateRes <- blockSigned.validate(publicKey)(cripto).rethrow
    } yield validateRes

    assertIOBoolean(r)

  }

}
