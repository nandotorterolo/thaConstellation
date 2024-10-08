package io.github.nandotorterolo.headers

import cats.data.NonEmptyList
import cats.parse.Parser
import cats.parse.Parser.char
import cats.parse.Parser.charIn
import cats.parse.Parser0
import cats.parse.Rfc5234._

/**
 * Common rules defined in RFC7230
 *
 * https://github.com/bblfish/httpSig/blob/main/rfc8941/shared/src/main/scala/run/cosy/http/headers/Rfc7230.scala
 *
 * @see
 *   [[https://tools.ietf.org/html/rfc7230#appendix-B]]
 */
object Rfc7230 {

  /* `tchar = "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+" / "-" / "." /
   *  "^" / "_" / "`" / "|" / "~" / DIGIT / ALPHA`
   */
  val tchar: Parser[Char] = charIn("!#$%&'*+-.^_`|~").orElse(digit).orElse(alpha)

  /* `token = 1*tchar` */
  val token: Parser[String] = tchar.rep.string

  /* `obs-text = %x80-FF` */
  val obsText: Parser[Char] = charIn(0x80.toChar to 0xff.toChar)

  /* `OWS = *( SP / HTAB )` */
  val ows: Parser0[Unit] = sp.orElse(htab).rep0.void

  /*https://tools.ietf.org/html/rfc7230#section-3.2.3 last paragraph */
  val bws: Parser0[Unit] = ows

  /*   qdtext         = HTAB / SP /%x21 / %x23-5B / %x5D-7E / obs-text */
  val qdText: Parser[Char] =
    charIn('\t', ' ', 0x21.toChar)
      .orElse(charIn(0x23.toChar to 0x5b.toChar))
      .orElse(charIn(0x5d.toChar to 0x7e.toChar))
      .orElse(obsText)

  val qdPairChar: Parser[Char] = charIn('\t', ' ').orElse(vchar).orElse(obsText)

  /* quoted-pair    = "\" ( HTAB / SP / VCHAR / obs-text ) */
  val quotedPair: Parser[Char] = char('\\') *> qdPairChar

  /*quoted-string  = DQUOTE *( qdtext / quoted-pair ) DQUOTE*/
  val quotedString: Parser[String] =
    surroundedBy(qdText.orElse(quotedPair).rep0.string, dquote)

  /* HTAB / SP / %x21-27 / %x2A-5B / %x5D-7E / obs-text */
  val cText =
    charIn('\t', ' ', 0x21.toChar)
      .orElse(charIn(0x21.toChar to 0x27.toChar))
      .orElse(charIn(0x2a.toChar to 0x5b.toChar))
      .orElse(charIn(0x5d.toChar to 0x7e.toChar))
      .orElse(obsText)
  /* "(" *( ctext / quoted-pair / comment ) ")" */
  val comment: Parser[String] = Parser.recursive[String] { (comment: Parser[String]) =>
    between(char('('), cText.orElse(quotedPair).orElse(comment).rep0.string, char(')'))
  }

  /* `1#element => *( "," OWS ) element *( OWS "," [ OWS element ] )` */
  def headerRep1[A](element: Parser[A]): Parser[NonEmptyList[A]] = {
    val prelude = (char(',') <* ows).rep0
    val tailOpt = (ows.with1 *> char(',') *> (ows.with1 *> element).?).rep0
    val tail    = tailOpt.map(_.collect { case Some(x) => x })

    (prelude.with1 *> element ~ tail).map {
      case (h, t) =>
        NonEmptyList(h, t)
    }
  }

  def listSep = Parser.char(',').surroundedBy(ows)

  private def surroundedBy[A](a: Parser0[A], b: Parser[Any]): Parser[A] = b *> a <* b

  private def between[A](a: Parser[Any], b: Parser0[A], c: Parser[Any]): Parser[A] = a *> b <* c

}
