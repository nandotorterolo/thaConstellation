package io.github.nandotorterolo.models

import cats.implicits.toShow
import cats.Show
import io.circe.syntax.EncoderOps
import io.circe.Encoder
import io.circe.Json
import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signature
import io.github.nandotorterolo.crypto.Signed

case class BlockSigned(hash: Hash, signature: Signature, message: Block) extends Signed[Block]

object BlockSigned {

  implicit val showImpl: Show[BlockSigned] =
    Show.show(b => s"""
                      |blockId: ${b.hash.v.toBase58}
                      |${b.message.show}""".stripMargin)

  implicit val codec: scodec.Codec[BlockSigned] =
    (Hash.codec :: Signature.codec :: Block.codec).as[BlockSigned]

  implicit val encodeBlock: Encoder[BlockSigned] = new Encoder[BlockSigned] {
    override def apply(b: BlockSigned): Json = Json.obj(
      ("hash", Json.fromString(b.hash.v.toBase58)),
      ("sign", Json.fromString(b.signature.v.toBase58)),
      ("address", b.message.asJson)
    )
  }

}
