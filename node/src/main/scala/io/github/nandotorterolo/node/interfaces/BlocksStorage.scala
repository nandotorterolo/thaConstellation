package io.github.nandotorterolo.node.interfaces

import io.github.nandotorterolo.models._

trait BlocksStorage[F[_]] {

  /**
   * Insert a block
   * In case of genesis block, no relationship with parent block is added
   * In other case, a relationship with parent block is created.
   * For each transaction, a new Transaction is created on Transaction storage
   * For each transaction, a pre condition is required, source and destion accounts must exist on Account Storage
   * @param block block
   * @param txs transactions
   * @return
   */
  def insert(block: BlockSigned, txs: Vector[TransactionSigned]): F[Either[ModelThrowable, BlockSigned]]

  /**
   * Get block at height
   * @param height height
   * @return
   */
  def getAtSequenceNumber(height: Int): F[Option[BlockSigned]]

  /**
   * Get block by id
   * @param blockId block id
   * @return
   */
  def get(blockId: BlockId): F[Either[ModelThrowable, BlockSigned]]

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
