package io.github.nandotorterolo.node.storage.arcadeDB.dsl

import com.arcadedb.schema.Type

case class VertexProperty(
    name: String,
    schemaType: Type
)
