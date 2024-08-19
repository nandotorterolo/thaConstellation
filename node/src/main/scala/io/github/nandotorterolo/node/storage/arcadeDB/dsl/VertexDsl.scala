package io.github.nandotorterolo.node.storage.arcadeDB.dsl

/**
 * Describe an ArcadDB vertex.
 */
trait VertexDsl[T] {

  /**
   * Then name of the vertex, also called typeName
   * @return
   */
  def name: String

  /**
   * properties
   * @return
   */
  def properties: Set[VertexProperty]

  /**
   * indices
   * @return
   */
  def indices: Set[VertexIndex]

}

object VertexDsl {

  def create[T](vertexName: String, vertexFactory: VertexFactory[T]): VertexDsl[T] =
    new VertexDsl[T] {

      /**
       * Then anme of the vertex
       *
       * @return
       */
      override def name: String = vertexName

      override def properties: Set[VertexProperty] = vertexFactory.properties

      override def indices: Set[VertexIndex] = vertexFactory.indices

    }

}
