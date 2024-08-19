package io.github.nandotorterolo.node.storage.arcadeDB.dsl

import com.arcadedb.schema.Schema.INDEX_TYPE

case class VertexIndex(
    propertyName: String,
    indexType: INDEX_TYPE,
    unique: Boolean
)
