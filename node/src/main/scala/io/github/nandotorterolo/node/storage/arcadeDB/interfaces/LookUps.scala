package io.github.nandotorterolo.node.storage.arcadeDB.interfaces

import cats.data.EitherT
import cats.effect.Async
import cats.implicits._
import com.arcadedb.database.Database
import com.arcadedb.database.RID
import com.arcadedb.graph.Vertex
import io.github.nandotorterolo.crypto.Signed
import io.github.nandotorterolo.models.ModelThrowable
import io.github.nandotorterolo.models.ModelThrowable.EntityNotFound
import io.github.nandotorterolo.models.ModelThrowable.Message
import io.github.nandotorterolo.node.storage.arcadeDB.dsl.CodecVertex

trait LookUps[T <: Signed[?]] {
  self: CodecVertex[T] =>

  val vertexName: String
  val keyName: String

  def lookupVertex[F[_]: Async](key: String)(db: Database): F[Either[ModelThrowable, Vertex]] = {
    (for {

      cursor <- EitherT(Async[F].delay(db.lookupByKey(vertexName, keyName, key)).attempt)
        .leftMap(th => Message(th.getMessage): ModelThrowable)

      vertex <- EitherT.cond[F](cursor.hasNext, cursor.next().asVertex(true), EntityNotFound: ModelThrowable)
    } yield vertex).value
  }

  def lookupVertexE(key: String)(db: Database): Either[ModelThrowable, Vertex] = {
    val cursor = db.lookupByKey(vertexName, keyName, key)
    Either.cond(cursor.hasNext, cursor.next().asVertex(true), EntityNotFound: ModelThrowable)
  }

  def lookupRID[F[_]: Async](rid: RID, loadContent: Boolean)(db: Database): F[Either[ModelThrowable, Vertex]] = {
    EitherT(Async[F].delay(db.lookupByRID(rid, loadContent).asVertex()).attempt)
      .leftMap(th => Message(th.getMessage): ModelThrowable)
      .value
  }

  def lookup[F[_]: Async](keyValue: String)(db: Database): F[Option[T]] = {
    Async[F].delay {
      val r = db.lookupByKey(vertexName, keyName, keyValue)
      if (r.hasNext) {
        Option(decode(r.next().asVertex(true)))
      } else None
    }
  }

}
