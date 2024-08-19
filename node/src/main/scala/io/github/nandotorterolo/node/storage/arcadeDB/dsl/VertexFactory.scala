package io.github.nandotorterolo.node.storage.arcadeDB.dsl

case class VertexFactory[T](
    encode: T => Map[String, AnyRef],
    properties: Set[VertexProperty],
    indices: Set[VertexIndex]
) {

  def withProperty(property: VertexProperty): VertexFactory[T] =
    copy(
      properties = properties.incl(property)
    )

  def withIndex(index: VertexIndex): VertexFactory[T] =
    copy(
      indices = indices.incl(index)
    )
}

object VertexFactory {
  def apply[T]: VertexFactory[T] = new VertexFactory[T](_ => Map.empty, Set.empty, Set.empty)
}
