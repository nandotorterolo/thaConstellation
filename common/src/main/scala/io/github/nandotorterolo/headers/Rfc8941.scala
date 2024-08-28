package io.github.nandotorterolo.headers

import java.math.MathContext
import java.math.RoundingMode

import scala.collection.immutable.ListMap

import scodec.bits.ByteVector

/**
 * Structured Field Values for HTTP [[https://www.rfc-editor.org/rfc/rfc8941.html RFC8941]]
 */
object Rfc8941 {

  //
  // types used by RFC8941
  //
  /**
   * see [[https://www.rfc-editor.org/rfc/rfc8941.html#section-3.3 §3.3 Items]] of RFC8941.
   */
  abstract class Item //   = SfInt | SfDec | SfString | Token | SfBinay | SfBoolean
  type Bytes  = ByteVector
  type Param  = (Token, Item)
  type Params = ListMap[Token, Item]
  type SfList = List[Parameterized]
  type SfDict = ListMap[Token, Parameterized]

  // warning this is public and there is an unsafeParse
  def Param(tk: String, i: Item): Param = (Token.unsafeParsed(tk), i)

  def Params(ps: Param*): Params = ListMap(ps: _*)

  def SfDict(entries: (Token, Parameterized)*): ListMap[Token, Parameterized] = ListMap(entries: _*)

  private def paramConversion(paras: Param*): Params = ListMap(paras: _*)

  sealed trait Parameterized {
    def params: Params
  }

  /**
   * SFInt's cover a subspace of Java Longs. An Opaque type would not do, as these need to be
   * pattern matched. Only the object constructor can build these
   */
  final case class SfInt private (val long: Long) extends Item

  /* https://www.rfc-editor.org/rfc/rfc8941.html#ser-decimal */
  final case class SfDec private (val double: Double) extends Item

  /**
   * class has to be abstract to remove the `copy` operation which would allow objects outside
   * this package to create illegal values.
   */
  final case class SfString private (val asciiStr: String) extends Item

  // class is abstract to remove copy operation
  final case class Token private (val tk: String) extends Item

  final case class SfBoolean(val b: Boolean) extends Item

  final case class SfBinary(val b: ByteVector) extends Item

  /**
   * dict-member = member-key ( parameters / ( "=" member-value )) member-value = sf-item /
   * inner-list
   *
   * @param key
   *   member-key
   * @param values
   *   if InnerList with an empty list, then we have "parameters", else we have an inner list
   */
  final case class DictMember(key: Token, values: Parameterized)

  /** Parameterized Item */
  final case class PItem[T <: Item](item: T, params: Params) extends Parameterized

  /** Inner List */
  final case class IList(items: List[PItem[?]], params: Params) extends Parameterized

  object SfInt {
    val MAX_VALUE: Long = 999_999_999_999_999L
    val MIN_VALUE: Long = -MAX_VALUE

    @throws[NumberFormatException]
    def apply(longStr: String): SfInt = apply(longStr.toLong)

    /** We throw a stackfree exception. Calling code can wrap in Try. */

    def apply(long: Long): SfInt = {
      if (long <= MAX_VALUE && long >= MIN_VALUE) new SfInt(long)
      else throw new NumberFormatException("long")
    }

    // no need to check bounds if parsed by parser below
    private[Rfc8941] def unsafeParsed(longStr: String): SfInt = new SfInt(longStr.toLong)
  }

  object SfDec {
    val MAX_VALUE: Double = 999_999_999_999.999
    val MIN_VALUE: Double = -MAX_VALUE
    val mathContext       = new MathContext(3, RoundingMode.HALF_EVEN)

    def apply(d: Double): SfDec =
      if (d <= MAX_VALUE && d >= MIN_VALUE)
        new SfDec(BigDecimal(d).setScale(3, BigDecimal.RoundingMode.HALF_EVEN).doubleValue)
      else throw new NumberFormatException("d")

    @throws[NumberFormatException]
    def apply(sfDecStr: String): SfDec = Parser.sfDecimal.parseAll(sfDecStr) match {
      case Left(err)  => throw new NumberFormatException(err.toString)
      case Right(num) => num
    }

    // no need to check bounds if parsed by parser below
    private[Rfc8941] def unsafeParsed(int: String, fract: String): SfDec =
      new SfDec(
        BigDecimal(int + "." + fract)
          .setScale(
            3,
            BigDecimal.RoundingMode.HALF_EVEN
          )
          .doubleValue
      )
  }

  object SfString {
    val bs = '\\'

    @throws[IllegalArgumentException]
    def apply(str: String): SfString = {
      if (str.forall(c => isAsciiChar(c.toInt))) new SfString(str)
      else throw new IllegalArgumentException(s"$str<<< contains non ascii chars ")
    }

    def isAsciiChar(c: Int): Boolean = (c > 0x1f) && (c < 0x7f)

    /* No danger of throwing an exception here, as tokens are subsets of SfString */
    def apply(token: Token): SfString = new SfString(token.tk)

    private[Rfc8941] def unsafeParsed(asciiStr: List[Char]): SfString =
      new SfString(asciiStr.mkString)
  }

  object Token {
    @throws[IllegalArgumentException]
    def apply(t: String): Token = Parser.sfToken.parseAll(t) match {

      case Right(value) => value
      case Left(err)    => throw new IllegalArgumentException(s"error parsing token $t failed at offset ${err.failedAtOffset}")
    }
    private[Rfc8941] def unsafeParsed(name: String) = new Token(name)

  }

  object SfBoolean {
    @throws[IllegalArgumentException]
    def apply(t: String): SfBoolean = Parser.sfBoolean.parseAll(t) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalArgumentException(s"error parsing token $t failed at offset ${err.failedAtOffset}")
    }
    private[Rfc8941] def unsafeParsed(name: String) = new SfBoolean(name.toBoolean)
  }

  object SfBinary {
    @throws[IllegalArgumentException]
    def apply(t: String): SfBinary = Parser.sfBinary.parseAll(t) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalArgumentException(s"error parsing token $t failed at offset ${err.failedAtOffset}")
    }
//    private[Rfc8941] def unsafeParsed(name: String) = new SfBinary(name.toBoolean)
  }

  object PItem {
    def apply[T <: Item](item: T): PItem[T] = new PItem[T](item, ListMap())

    def apply[T <: Item](item: T, params: Param*): PItem[T] = new PItem(item, ListMap(params: _*))
  }

  object Parser {
    import cats.parse.{ Parser => P }
    import cats.parse.{ Parser0 => P0 }
    import cats.parse.{ Rfc5234 => R5234 }
    import io.github.nandotorterolo.headers.Rfc7230.ows

    // these have to be at the top, or all the vals below have to be lazy
    private val `*`           = P.charIn('*')
    private val `:`           = P.char(':')
    private val `?`           = P.char('?')
    private val `.`           = P.char('.')
    private val bs            = '\\'
    private val minus         = P.char('-')
    private val `\\`: P[Unit] = P.char(bs)

    lazy val boolean: P[Boolean] = R5234.bit.map(_ == '1')

    val sfBoolean: P[SfBoolean] = (`?` *> boolean).map{b => Rfc8941.SfBoolean(b)}

    val sfInteger: P[SfInt] =
      (minus.?.with1 ~ R5234.digit.rep(1, 15)).string.map(s => Rfc8941.SfInt.unsafeParsed(s))

    val decFraction: P[String] = R5234.digit.rep(1, 3).string

    val signedDecIntegral: P[String] =
      (minus.?.with1 ~ R5234.digit.rep(1, 12)).map {
        case (min, i) =>
          min.map(_ => "-").getOrElse("") + i.toList.mkString
      }

    val sfDecimal: P[SfDec] =
      (signedDecIntegral ~ (`.` *> decFraction)).map {
        case (dec, frac) =>
          SfDec.unsafeParsed(dec, frac.toList.mkString) // todo: keep the non-empty list?
      }
    // first we have to check for decimals, then for integers, or there is the risk that the `.` won't be noticed
    // todo: optimisation would remove backtracking here
    val sfNumber: P[Item] = sfDecimal.backtrack.orElse(sfInteger)

    val unescaped: P[Char] =
      P.charIn(' ', 0x21.toChar)
        .orElse(P.charIn(0x23.toChar to 0x5b.toChar))
        .orElse(P.charIn(0x5d.toChar to 0x7e.toChar))

    val escaped: P[Char] = `\\` *> (P.charIn(bs, '"'))

    val sfString: P[SfString] = (R5234.dquote *> (unescaped | escaped).rep0 <* R5234.dquote)
      .map(chars => SfString.unsafeParsed(chars))

    val sfToken: P[Token] =
      ((R5234.alpha | P.charIn('*')) ~ (Rfc7230.tchar | P.charIn(':', '/')).rep0)
        .map { case (c, lc) => Token.unsafeParsed((c :: lc).mkString) }

    val base64: P[Char] = R5234.alpha | R5234.digit | P.charIn('+', '/', '=')

    val sfBinary: P[SfBinary] = (`:` *> base64.rep.string <* `:`)
      .map { chars =>
        ByteVector.fromValidBase64(chars, scodec.bits.Bases.Alphabets.Base64)
      }
      .map(bv => SfBinary(bv))

    val bareItem: P[Item] =
      P.oneOf(sfNumber :: sfString :: sfToken :: sfBinary :: sfBoolean :: Nil)

    val lcalpha: P[Char] = P.charIn(0x61.toChar to 0x7a.toChar) | P.charIn('a' to 'z')

    val key: P[Token] =
      ((lcalpha | `*`) ~ (lcalpha | R5234.digit | P.charIn('_', '-', '.', '*')).rep0)
        .map { case (c, lc) => Token.unsafeParsed((c :: lc).mkString) }

    val parameter: P[Param] =
      key ~ (P.char('=') *> bareItem).orElse(P.pure(SfBoolean(true)))

    // note: parameters always returns an answer (the empty list) as everything can have parameters
    // todo: this is not exeactly how it is specified, so check here if something goes wrong
    val parameters: P0[Params] =
      (P.char(';') *> ows *> parameter).rep0.orElse(P.pure(List())).map { list =>
        ListMap.from[Token, Item](list.iterator)
      }
    val sfItem: P[PItem[Item]] = (bareItem ~ parameters).map { case (item, params) => PItem(item, params) }

    val innerList: P[IList] = {

      import R5234.sp
      (((P.char('(') ~ sp.rep0) *> (sfItem ~ ((sp.rep(1) *> sfItem).backtrack.rep0) <* sp.rep0).? <* P.char(')')) ~ parameters)
        .map {
          case (Some((pi, lpi)), params) => IList(pi :: lpi, params)
          case (None, params)            => IList(List(), params)
        }
    }

    val listMember: P[Parameterized] = sfItem | innerList

    val sfList: P[SfList] =
      (listMember ~ ((ows *> P.char(',') *> ows).void.with1 *> listMember).rep0).map { case (p, lp) => p :: lp }

    val memberValue: P[Parameterized] = sfItem | innerList

    // note: we have to go with parsing `=` first as parameters always returns an answer.
    val dictMember: P[DictMember] = (key ~ (P.char('=') *> memberValue).eitherOr(parameters))
      .map {
        case (k, Left(parameters))     => DictMember(k, PItem(SfBoolean(true), parameters))
        case (k, Right(parameterized)) => DictMember(k, parameterized)
      }
    val sfDictionary: P[SfDict] =
      (dictMember ~ ((ows *> P.char(',') *> ows).with1 *> dictMember).rep0).map {
        case (dm, list) =>
          val x: List[DictMember] = dm :: list
          // todo: avoid this tupling
          ListMap.from(x.map((d: DictMember) => d.key -> d.values)) // Tuple.fromProductTyped(d)))
      }
  }

}
