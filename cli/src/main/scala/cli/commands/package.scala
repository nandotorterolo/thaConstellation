package cli

import org.http4s.Uri

package object commands {

  // TODO move to some config
  private val url                  = "http://localhost:8080"
  val registerUri: Uri             = Uri.unsafeFromString(s"$url/account/register")
  val balanceUri: Uri              = Uri.unsafeFromString(s"$url/account/balance")
  val transactionByAccountUri: Uri = Uri.unsafeFromString(s"$url/account/transactions")
  val broadcastUri: Uri            = Uri.unsafeFromString(s"$url/transaction/broadcast")
  val txByIdUri: Uri               = Uri.unsafeFromString(s"$url/transaction/byId")
  val blockUri: Uri                = Uri.unsafeFromString(s"$url/block")

}
