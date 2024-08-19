package io.github.nandotorterolo.node.storage.arcadeDB.dsl

import com.arcadedb.database.Database
import com.arcadedb.graph.MutableVertex
import com.arcadedb.graph.Vertex

trait CodecVertex[T] {

  def encode(account: T)(db: Database): MutableVertex
  def decode(v: Vertex): T

}
