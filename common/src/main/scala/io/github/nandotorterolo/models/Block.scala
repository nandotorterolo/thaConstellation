package io.github.nandotorterolo.models

import cats.Show
import io.circe.Encoder
import io.circe.Json
import io.github.nandotorterolo.crypto.Hash
import io.github.nandotorterolo.crypto.Signable
import io.github.nandotorterolo.crypto.Signature
import scodec.bits.ByteVector

/**
 * Reference to prior block hash
 * Signature attached corresponding to hash of the data of the block signed by the keypair of the current (single process) node.
 * Invalid to contain multiple conflicting transactions
 *
 * Genesis block will contain a dummy blockId, instead of being optional
 * @param priorBlock Reference to prior block hash
 * @param transactions Collection of or sequence of transactions
 * @param sequenceNumber Monotonically increasing block sequence number
 */
case class Block(
    priorBlock: BlockId,
    sequenceNumber: Int,
    transactions: Vector[TransactionId]
) extends Signable[Block, BlockSigned] {
  override val encodeMe: Block = this

  override def build: (Hash, Signature, Block) => BlockSigned = BlockSigned(_, _, _)
}

object Block {

  private val dummyPriorBlockId: ByteVector = ByteVector.fill(64)(0.toByte)

  def isGenesisBlock(block: Block): Boolean = block.priorBlock.value.compare(dummyPriorBlockId) == 0

  val unsignedGenesisBlock: Block = Block(
    transactions = Vector.empty,
    sequenceNumber = 0,
    priorBlock = BlockId(dummyPriorBlockId)
  )

  implicit val showBlock: Show[Block] =
    Show.show(b => s"""
                      |sequenceNumber: ${b.sequenceNumber}
                      |priorBlock: ${b.priorBlock.value.toBase58}
                      |transactions: ${b.transactions.map(_.value.toBase58).mkString(",")}
                      |""".stripMargin)

  implicit val encoder: Encoder[Block] = new Encoder[Block] {
    override def apply(b: Block): Json = Json.obj(
      ("priorBlock", Json.fromString(b.priorBlock.value.toBase58)),
      ("sequenceNumber", Json.fromInt(b.sequenceNumber)),
      ("transactions", Json.fromString(b.transactions.map(_.value.toBase58).mkString(","))),
    )
  }

  implicit val codec: scodec.Codec[Block] =
    (
      BlockId.codec ::
        scodec.codecs.int32 ::
        scodec.codecs.vector(TransactionId.codec)
    ).as[Block]

}
