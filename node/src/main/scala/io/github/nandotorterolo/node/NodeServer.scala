package io.github.nandotorterolo.node

import java.security.Security

import scala.concurrent.duration.DurationInt

import cats.effect.implicits.genSpawnOps
import cats.effect.std.Queue
import cats.effect.Async
import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.all._
import com.arcadedb.server.ArcadeDBServer
import com.arcadedb.ContextConfiguration
import com.comcast.ip4s._
import fs2.io.net.Network
import io.github.nandotorterolo.crypto.EcdsaBCEncryption
import io.github.nandotorterolo.models.TransactionSigned
import io.github.nandotorterolo.node.routes.BalanceRoute
import io.github.nandotorterolo.node.routes.BlockRoute
import io.github.nandotorterolo.node.routes.HealthRoute
import io.github.nandotorterolo.node.routes.RegistrationRoute
import io.github.nandotorterolo.node.routes.TransactionBroadcastRoute
import io.github.nandotorterolo.node.routes.TransactionByIdRoute
import io.github.nandotorterolo.node.routes.TransactionsByAccountRoute
import io.github.nandotorterolo.node.service.HealthService
import io.github.nandotorterolo.node.service.MemPoolServiceQueue
import io.github.nandotorterolo.node.service.ServerCredentialsImpl
import io.github.nandotorterolo.node.service.StorageServiceImpl
import io.github.nandotorterolo.node.service.TransactionsMultipleFilterImpl
import io.github.nandotorterolo.node.storage.arcadeDB.dsl.SchemaFactory
import io.github.nandotorterolo.node.storage.AccountsArcadeDBImpl
import io.github.nandotorterolo.node.storage.BlocksArcadeDBImpl
import io.github.nandotorterolo.node.storage.TransactionArcadeDBImpl
import io.github.nandotorterolo.node.validator._
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

object NodeServer {

  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  def run[F[_]: Async: Network: Logger]: F[Nothing] = {

    val server = for {
      // required at start up
      _ <- Resource.eval(
        Async[F].delay(Security.addProvider(new BouncyCastleProvider()))
      ) // TODO delete

      arcadeDBServer = new ArcadeDBServer(new ContextConfiguration())
      _ <- Resource.make(Async[F].blocking(arcadeDBServer.start()))(_ => Async[F].blocking(arcadeDBServer.stop()))

      db <- Resource.eval(Async[F].blocking(arcadeDBServer.getOrCreateDatabase("Node")).attempt).rethrow
      _  <- Resource.eval(SchemaFactory.make(db.getSchema)).rethrow

      accountsStorage    = AccountsArcadeDBImpl.build[F](db)
      blocksStorage      = BlocksArcadeDBImpl.build[F](db)
      transactionStorage = TransactionArcadeDBImpl.build[F](db)

      cripto            = EcdsaBCEncryption.build[F]
      serverCredentials = ServerCredentialsImpl.build[F](cripto)

      storageService = StorageServiceImpl.build[F](cripto, accountsStorage, blocksStorage, transactionStorage, serverCredentials)
      healthService  = HealthService.build[F]

      // Validators
      tv   = TransactionValidator.build[F]
      tsv  = TransactionSignedValidatorImpl.build[F](cripto, storageService)
      tov  = TransactionOverdraftValidatorImpl.build[F](storageService)
      tnv  = TransactionNonceValidatorImpl.build[F](storageService)
      tmnv = TransactionsMultipleNonceValidatorImpl.build[F](storageService)
      tmov = TransactionsMultipleOverdraftValidatorImpl.build[F](storageService)
      tmf  = TransactionsMultipleFilterImpl.build[F](tmnv, tmov)

      // If is the first time that we run the chain, a genesis block is create, and an server account
      _ <- Resource.eval(blocksStorage.isEmpty).flatMap { isEmpty =>
        Resource.eval {
          if (isEmpty) {
            for {
              _ <- Logger[F].info(s"The chain is empty:").attempt
              _ <- storageService.createGenesisBlock()
              _ <- storageService.createServerAccount()
              _ <- Logger[F].info(s"Genesis block and account server, were created.").attempt
            } yield ()
          } else { Async[F].unit }
        }
      }

      // TODO Console debug code, remove it later
      _ <- Resource.eval(blocksStorage.getAtSequenceNumber(0).flatMap {
        case Some(b) => Logger[F].info(show"Root chain: $b")
        case _       => Logger[F].error(show"Error!")
      })
      // TODO Console debug code, remove it later
      _ <- Resource.eval(storageService.getServerAccount.flatMap {
        case Right(accountSigned) =>
          Logger[F].info(show"Server Account Address: $accountSigned")
        case Left(s) => Logger[F].error(show"Error: $s")
      })

      queue <- Resource.eval(Queue.bounded[F, TransactionSigned](10))
      pool = new MemPoolServiceQueue(queue, storageService, tmf, serverCredentials, cripto)

      // Print information on console about the current mempool size
      _ <- (Async[F].sleep(5.seconds) >> queue.size.flatMap(i => Logger[F].info(s"Mempool Size: $i"))).foreverM.background.void.start

      // Deque elements and try to pack a block, Passing None will try to dequeue the whole queue.
      _ <- (Async[F]
        .sleep(20.seconds) >> queue
        .tryTakeN(None)
        .flatMap(l => pool.processListTxs(l.toVector))).foreverM.background.void.start

      httpApp =
        (
          RegistrationRoute.route[F](cripto, storageService, pool)
            <+> BalanceRoute.route[F](cripto, storageService)
            <+> TransactionsByAccountRoute.route[F](cripto, storageService)
            <+> TransactionBroadcastRoute.route[F](tv, tsv, tov, tnv, pool)
            <+> TransactionByIdRoute.route[F](cripto, storageService)
            <+> BlockRoute.route[F](storageService)
            <+> HealthRoute.route[F](healthService)
        ).orNotFound

      finalHttpApp = org.http4s.server.middleware.Logger.httpApp(true, true)(httpApp)

      _ <-
        EmberServerBuilder
          .default[F]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"8080")
          .withHttpApp(finalHttpApp)
          .build

    } yield ()

//    server.onFinalize(accountStorage.close()).useForever
    server.useForever

  }
}
