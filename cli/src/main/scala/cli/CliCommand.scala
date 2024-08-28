package cli

sealed abstract class CliCommand
object CliCommand {
  case object CliQuit extends CliCommand
  case object CliHelp extends CliCommand

  case object CliMenu extends CliCommand

  case object CliAccountMenu         extends CliCommand
  case object CliAccountInspect      extends CliCommand
  case object CliAccountGenerateKP   extends CliCommand
  case object CliAccountRegistration extends CliCommand
  case object CliAccountBalance      extends CliCommand
  case object CliAccountTransactions extends CliCommand

  case object CliBlockMenu        extends CliCommand
  case object CliBlockById        extends CliCommand
  case object CliBlockByIdV2      extends CliCommand
  case object CliBlockBySeqNumber extends CliCommand

  case object CliTransactionMenu   extends CliCommand
  case object CliTransactionById   extends CliCommand
  case object CliTransactionCreate extends CliCommand

}
