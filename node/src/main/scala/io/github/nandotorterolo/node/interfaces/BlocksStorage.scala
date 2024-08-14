package io.github.nandotorterolo.node.interfaces

import io.github.nandotorterolo.models.Block
import io.github.nandotorterolo.models.BlockId
import io.github.nandotorterolo.models.BlockSigned
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.TransactionSigned

trait BlocksStorage[F[_]] {

  def insert(block: BlockSigned, txs: Vector[TransactionSigned]): F[Either[ModelThrowable, BlockSigned]]

  /**
   * Get block at height
   * @param height height
   * @return
   */
  def getAtSequenceNumber(height: Int): F[Option[BlockSigned]]

  def get(blockId: BlockId): F[Either[ModelThrowable, Block]]

  def close(): F[Unit]

  /**
   * is the block chain empty
   * @return
   */
  def isEmpty: F[Boolean]

  /**
   * Get height of the chain
   * @return
   */
  def getHeight: F[Int]

}
