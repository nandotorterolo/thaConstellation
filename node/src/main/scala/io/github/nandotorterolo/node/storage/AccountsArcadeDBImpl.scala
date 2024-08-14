package io.github.nandotorterolo.node.storage

import cats.effect.Async
import cats.implicits._
import com.arcadedb.database.Database
import io.github.nandotorterolo.models.Account
import io.github.nandotorterolo.models.AccountSigned
import io.github.nandotorterolo.models.Address
import io.github.nandotorterolo.models.AddressId
import io.github.nandotorterolo.node.interfaces.AccountsStorage
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import scodec.bits.ByteVector
import scodec.Attempt
import ArcadeDB._

class AccountsArcadeDBImpl[F[_]: Async](db: Database) extends AccountsStorage[F] {

  implicit def logger: Logger[F] = Slf4jLogger.getLogger[F]

  override def insert(accountSigned: AccountSigned): F[Boolean] = {

    Async[F]
      .delay { AccountSigned.codec.encode(accountSigned) }
      .flatMap {
        case Attempt.Successful(value) =>
          Async[F]
            .delay {

              db.begin()
              db.newVertex(AccountVertexName)
                .set("key", accountSigned.message.address.addressId.value.toBase58)
                .set("balance", accountSigned.message.balance)
                .set("latestUsedNonce", accountSigned.message.latestUsedNonce)
                .set("address", accountSigned.message.address.addressId.value.toArray)
                .set("publicKey", accountSigned.message.address.publicKey.toArray)
                .set("value", value.toByteArray)
                .save()

              db.commit()
            }
            .map(_ => true)
            .recoverWith {
              case ex: Exception =>
                db.rollback()
                logger.warn(s"ex $ex").flatMap(_ => false.pure[F])

            }
        case Attempt.Failure(cause) => false.pure[F].flatTap(_ => Logger[F].warn(cause.message))
      }

  }

  override def contains(accountId: AddressId): F[Boolean] = Async[F].delay {
    db.lookupByKey(AccountVertexName, "key", accountId.value.toBase58).hasNext
  }

  override def close(): F[Unit] = Async[F].delay { db.close() }

  override def get(accountId: AddressId): F[Option[Account]] =
    Async[F].delay {
      val r = db.lookupByKey(AccountVertexName, "key", accountId.value.toBase58)
      if (r.hasNext) {
        val vertex = r.next().asVertex(true)
        Account(
          address = Address(AddressId(ByteVector(vertex.getBinary("address"))), ByteVector(vertex.getBinary("publicKey"))),
          balance = vertex.getDouble("balance"),
          latestUsedNonce = vertex.getInteger("latestUsedNonce")
        ).some
      } else None

    }
}

object AccountsArcadeDBImpl {
  def build[F[_]: Async](db: Database) = new AccountsArcadeDBImpl[F](db)
}
