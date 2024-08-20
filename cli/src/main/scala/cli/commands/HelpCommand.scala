package cli.commands

import cats.effect.std.Console
import cats.effect.Sync
import cli._

class HelpCommand[F[_]: Sync: Console] {
  def command(cmd: Command[Unit]): CommandT[F, Unit] =
    write[F]("""
               |- Account:
               |---- gen: Generates key private public keys (alias: generateKeyPair)
               |---- inspect: See address of Publick key
               |---- reg: Register keys on chain (alias: registration)
               |---- bal: Get balance and other account's detail  (alias: balance)
               |---- txs: Get all transaction associated an account, where account id was source or destination
               |
               |- Block:
               |---- byId: Get block information given blockId
               |---- bySN: Get block information given a sequence number (alias: bySeqNumber)
               |
               |- Transaction:
               |---- byId: Get transaction information given transactionId
               |---- create: Create a transacion
               |
               |- All menus common commands:
               |---- back: Go back to the previuos menu (alias: b)
               |---- help: show help (alias: h)
               |---- quit: Exit cli (alias: q)
               |""".stripMargin).subflatMap(_ => cmd)
}

object HelpCommand {
  def apply[F[_]: Sync: Console](cmd: Command[Unit]): CommandT[F, Unit] = new HelpCommand[F].command(cmd)
}
