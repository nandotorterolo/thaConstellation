package headers

import scala.annotation.nowarn
import scala.collection.immutable.ArraySeq
import scala.collection.immutable.ListMap

import cats.parse.Parser
import io.github.nandotorterolo.headers.Rfc8941._
import io.github.nandotorterolo.headers.Rfc8941.Parser._
import scodec.bits.ByteVector

class Rfc8941Spec extends munit.FunSuite {

  def R[T](value: T, remaining: String = ""): Right[Parser.Error, (String, T)] = Right(remaining, value)

  def RA[T](value: T): Right[Parser.Error, T] = Right(value)

  def parseFail[T](result: Either[Parser.Error, (String, T)], @nowarn msg: String = "")(implicit loc: munit.Location): Unit = {
    assert(result.isLeft, result)
  }

  def parseFailAll[T](result: Either[Parser.Error, T], @nowarn msg: String = "")(implicit loc: munit.Location): Unit = {
    assert(result.isLeft, result)
  }

  test("test sfBoolean") {
    assertEquals(sfBoolean.parse("?0"), R(SfBoolean(false)))
    assertEquals(sfBoolean.parse("?1"), R(SfBoolean(true)))
    parseFail(sfBoolean.parse("?x"))
    parseFail(sfBoolean.parse("000"))
  }

  test("test sfInteger syntax") {
    assertEquals(SfInt("42").long, 42L)
    assertEquals(SfInt("-42").long, -42L)
    assertEquals(SfInt(42L).long, 42L)
    intercept[NumberFormatException] {
      SfInt(("999" * 5) + "0")
    }
  }

  test("test sfInteger") {
    assertEquals(sfInteger.parse("42"), R(SfInt("42")))
    assertEquals(sfInteger.parse("123456789012345"), R(SfInt("123456789012345")))
    assertEquals(sfInteger.parse("42.5"), R(SfInt("42"), ".5"))
    parseFailAll(sfInteger.parseAll("123hello"))
    parseFail(sfInteger.parse("hello"))
  }

  test("test sfDecimal syntax") {
    assertEquals(SfDec("42.0").double, 42.0)
    assertEquals(SfDec("-42.01").double, -42.01)
    assertEquals(SfDec("-42.011").double, -42.011)
    assertEquals(SfDec("-42.015").double, -42.015)
    assertEquals(SfDec(42.0015).double, 42.002)
    intercept[NumberFormatException] {
      SfDec(SfDec.MAX_VALUE + 1).double
    }
    intercept[NumberFormatException] {
      SfDec("999OO0")
    }
    intercept[NumberFormatException] {
      SfDec(("999" * 4) + "0")
    }
    intercept[NumberFormatException] {
      SfDec(("999" * 6) + "0")
    }
  }

  test("test sfDecimal") {
    assertEquals(sfDecimal.parse("42.0"), R(SfDec("42.0")))
    assertEquals(sfDecimal.parse("123456789012.123"), R(SfDec("123456789012.123")))
    assertEquals(sfDecimal.parse("42.5"), R(SfDec("42.5")))
    parseFail(sfDecimal.parse("123456789012345.123"), "the dot is too far away")
    parseFail(sfDecimal.parse("123"), "there is not dot")
  }

  test("test sfNumber") {
    assertEquals(sfNumber.parse("42.0"), R(SfDec("42.0")))
    assertEquals(sfNumber.parse("123456789012.123"), R(SfDec("123456789012.123")))
    assertEquals(sfNumber.parse("-123456789012.123"), R(SfDec("-123456789012.123")))
    assertEquals(sfNumber.parse("42.5"), R(SfDec("42.5")))
    assertEquals(sfNumber.parse("-123456789012345.123"), R(SfInt("-123456789012345"), ".123"))
    assertEquals(sfNumber.parse("123"), R(SfInt("123")))
    assertEquals(sfNumber.parse("-123"), R(SfInt("-123")))
    parseFail(sfNumber.parse("a123"), "does not start with digit")
  }

  test("test sfString syntax") {
    assertEquals(SfString("hello"), SfString("hello"))
    assertEquals(SfString("""hello\"""), SfString("""hello\"""))
    assertEquals(SfString(s"molae=${22 + 20}"), SfString("molae=42"))
    intercept[IllegalArgumentException] {
      SfString("être")
    }
    intercept[IllegalArgumentException] {
      SfString("	hi") // no tabs
    }
  }

  test("test sfString") {
    assertEquals(sfString.parseAll(""""42""""), RA(SfString("42")))
    assertEquals(sfString.parseAll(""""123456789012345""""), RA(SfString("123456789012345")))
    assertEquals(sfString.parseAll(""""a42b""""), RA(SfString("a42b")))
    assertEquals(sfString.parseAll(""""a\"42\\b""""), RA(SfString("""a"42\b""")))
    parseFail(sfString.parse(""""123456789012345"""), "no end quote")
    parseFail(sfString.parse(""""Bahnhofstraße"""), "no german here")
    parseFailAll(sfString.parseAll("""a"123hello""""), "letter before quote")
    parseFail(sfString.parse(""" "hello" """), "space before quote")
  }

  test("test sfToken") {
    assertEquals(sfToken.parseAll("foo123/456"), RA(Token("foo123/456")))
    assertEquals(sfToken.parseAll("*logicomix:"), RA(Token("*logicomix:")))
    assertEquals(sfToken.parseAll("*!#$%&'*+-.^_"), RA(Token("*!#$%&'*+-.^_")))
    assertEquals(sfToken.parseAll("goodmorning"), RA(Token("goodmorning")))
    parseFail(sfToken.parse("!hello"), "can't start with !")
    parseFailAll(sfToken.parseAll("#good morning"), "can't start with #")
    parseFailAll(sfToken.parseAll(" goodmorning"), "can't start with space")
    parseFailAll(sfToken.parseAll("good morning"), "can't contain space")
    parseFail(sfToken.parse(""" "hello" """), "space before quote")
  }

  test("test sfBinary") {
    // we use byte vector but go through Base64 lib, just for another test
    val expectedValue = ByteVector.fromBase64("cHJldGVuZCB0aGlzIGlzIGJpbmFyeSBjb250ZW50Lg==").get

    val cafebabe   = ArraySeq[Byte](113, -89, -34, 109, -90, -34)
    val cafebabeBV = ByteVector.view(cafebabe.toArray)
    val cafedead   = ArraySeq[Byte](113, -89, -34, 117, -26, -99)
    val cafedeadBV = ByteVector.view(cafedead.toArray)

    assertEquals(
      sfBinary.parse(":cHJldGVuZCB0aGlzIGlzIGJpbmFyeSBjb250ZW50Lg==:"),
      R(SfBinary(expectedValue))
    )
    assertEquals(sfBinary.parseAll(":cafebabe:"), RA(SfBinary(cafebabeBV)))
    assertEquals(sfBinary.parseAll(":cafedead:"), RA(SfBinary(cafedeadBV)))
    parseFailAll(sfBinary.parseAll(" :cafedead:"), "can't start with space")
    parseFailAll(
      sfBinary.parseAll(":cHJldGVuZCB0aGlzIGlzIGJpbmFyeSBjb250ZW50Lg"),
      "must finish with colon"
    )
    parseFailAll(
      sfBinary.parseAll(":cHJldGVuZCB0aGlz#IGlzIGJpbmFyeSBjb250ZW50Lg:"),
      "no hash in the middle"
    )
  }

  test("test sfList") {
    val cafebabe = ArraySeq[Byte](113, -89, -34, 109, -90, -34)
    assertEquals(
      sfList.parseAll("sugar, tea, rum"),
      RA(List(PItem(Token("sugar")), PItem(Token("tea")), PItem(Token("rum"))))
    )
    assertEquals(
      sfList.parseAll("sugar,tea,rum"),
      RA(List(PItem(Token("sugar")), PItem(Token("tea")), PItem(Token("rum"))))
    )
    assertEquals(
      sfList.parseAll("sugar, tea ,   rum"),
      RA(List(PItem(Token("sugar")), PItem(Token("tea")), PItem(Token("rum"))))
    )
    assertEquals(
      sfList.parseAll(""""sugar" , "tea",   "rum""""),
      RA(List(PItem(SfString("sugar")), PItem(SfString("tea")), PItem(SfString("rum"))))
    )
    assertEquals(
      sfList.parseAll("123.45 , 34.33, 42, 56.789"),
      RA(List(PItem(SfDec("123.45")), PItem(SfDec("34.33")), PItem(SfInt("42")), PItem(SfDec("56.789"))))
    )

    assertEquals(
      sfList.parseAll("""123.450 , 034.33, 42, foo123/456 , ?0  ,  ?1, "rum", :cafebabe:"""),
      RA(
        List(
          PItem(SfDec("123.450")),
          PItem(SfDec("034.33")),
          PItem(SfInt("42")),
          PItem(Token("foo123/456")),
          PItem(SfBoolean(false)),
          PItem(SfBoolean(true)),
          PItem(SfString("rum")),
          PItem(SfBinary(ByteVector(cafebabe)))
        )
      )
    )

    assertEquals(
      sfList.parseAll("""123.450 , 42, foo123/456 , ?0, "No/No", :cafebabe:"""),
      RA(
        List(
          PItem(SfDec("123.450")),
          PItem(SfInt("42")),
          PItem(Token("foo123/456")),
          PItem(SfBoolean(false)),
          PItem(SfString("No/No")),
          PItem(SfBinary(ByteVector(cafebabe.toArray)))
        )
      )
    )

    assertEquals(
      sfList.parseAll(
        """1234.750;  n=4;f=3 , 42;magic="h2g2", foo123/456;lang=en ,
          |   ?0;sleep=?1, "No/No", :cafebabe:;enc=unicode""".stripMargin
          .filter(
            _ != '\n'
          )
          .toString
      ),
      RA(
        List(
          PItem(SfDec("1234.750"), ListMap(Token("n") -> SfInt("4"), Token("f") -> SfInt("3"))),
          PItem(SfInt("42"), ListMap(Token("magic") -> SfString("h2g2"))),
          PItem(Token("foo123/456"), ListMap(Token("lang") -> Token("en"))),
          PItem(SfBoolean(false), ListMap(Token("sleep") -> SfBoolean(true))),
          PItem(SfString("No/No")),
          PItem(SfBinary(ByteVector.view(cafebabe.toArray)), ListMap(Token("enc") -> Token("unicode")))
        )
      )
    )
  }

  test("test inner Lists") {
    assertEquals(
      innerList.parseAll("""("foo" "bar")"""),
      RA(IList(List(PItem(SfString("foo")), PItem(SfString("bar"))), ListMap.empty))
    )
    assertEquals(
      innerList.parseAll("""(  "foo"  "bar")"""),
      RA(IList(List(PItem(SfString("foo")), PItem(SfString("bar"))), ListMap.empty))
    )
    assertEquals(
      innerList.parseAll("""(  "foo"  "bar"   )"""),
      RA(IList(List(PItem(SfString("foo")), PItem(SfString("bar"))), ListMap.empty))
    )
  }

  test("test lists of innerList") {
    assertEquals(
      sfList.parse("""("foo" "bar"), ("baz"), ("bat" "one"), ()"""),
      R(
        List(
          IList(List(PItem(SfString("foo")), PItem(SfString("bar"))), ListMap.empty),
          IList(List(PItem(SfString("baz"))), ListMap.empty),
          IList(List(PItem(SfString("bat")), PItem(SfString("one"))), ListMap.empty),
          IList(List.empty, ListMap.empty)
        )
      )
    )

    assertEquals(
      sfList.parse("""("foo"; a=1;b=2);lvl=5, ("bar" "baz");lvl=1"""),
      R(
        List(
          IList(
            List(PItem(SfString("foo"), ListMap(Token("a") -> SfInt("1"), Token("b") -> SfInt("2")))),
            ListMap(Token("lvl") -> SfInt("5"))
          ),
          IList(List(PItem(SfString("bar")), PItem(SfString("baz"))), ListMap(Token("lvl") -> SfInt("1")))
        )
      )
    )

  }

  test("dict-member") {
    assertEquals(
      dictMember.parse("""en="Applepie""""),
      R(DictMember(Token("en"), PItem(SfString("Applepie"))))
    )
  }

  test("sfDictionary") {
    val cafebabe = ArraySeq[Byte](113, -89, -34, 109, -90, -34)
    assertEquals(
      sfDictionary.parse("""en="Applepie", da=:cafebabe:"""),
      R(
        ListMap(
          Token("en") -> PItem(SfString("Applepie")),
          Token("da") -> PItem(SfBinary(ByteVector(cafebabe)))
        )
      )
    )

    assertEquals(
      sfDictionary.parse("""a=?0, b, c; foo=bar"""),
      R(
        SfDict(
          Token("a") -> PItem(SfBoolean(false)),
          Token("b") -> PItem(SfBoolean(true)),
          Token("c") -> PItem(SfBoolean(true), Token("foo") -> Token("bar"))
        )
      )
    )

    assertEquals(
      sfDictionary.parse("""a=(1 2), b=3, c=4;aa=bb"""),
      R(
        SfDict(
          Token("a") -> IList(List(PItem(SfInt("1")), PItem(SfInt("2"))), ListMap.empty),
          Token("b") -> PItem(SfInt("3")),
          Token("c") -> PItem(SfInt("4"), Token("aa") -> Token("bb")),
        )
      )
    )

    assertEquals(
      sfDictionary.parse("""a=(1 2), b=3, c=4;aa=bb, d=(5 6);valid"""),
      R(
        SfDict(
          Token("a") -> IList(List(PItem(SfInt("1")), PItem(SfInt("2"))), ListMap.empty),
          Token("b") -> PItem(SfInt("3")),
          Token("c") -> PItem(SfInt("4"), Token("aa") -> Token("bb")),
          Token("d") -> IList(List(PItem(SfInt("5")), PItem(SfInt("6"))), ListMap(Token("valid") -> SfBoolean(true)))
        )
      )
    )
  }

  test("sfDictionary with Signing Http Messages headers") {

    /**
     * following [[https://tools.ietf.org/html/rfc8792#section-7.2.2 RFC8792 §7.2.2]] single
     * slash line unfolding algorithm
     */
    def rfc8792single(str: String): String = {
      val singleSlsh = raw"\\\n[\t ]*".r
      singleSlsh.replaceAllIn(str.stripMargin, "")
    }

    val `ex§4.1` =
      rfc8792single("""sig1=("@request-target" "host" "date"   "cache-control" \
			  |      "x-empty-header" "x-example" "x-example-dict";sf); keyid="test-key-a"; \
			  |       alg="rsa-pss-sha512"; created=1402170695; expires=1402170995\
			  |""")

    assertEquals(
      sfDictionary.parseAll(`ex§4.1`),
      RA(
        ListMap[Token, Parameterized](
          Token("sig1") ->
            IList(
              items = List(
                PItem(SfString("@request-target")),
                PItem(SfString("host")),
                PItem(SfString("date")),
                PItem(SfString("cache-control")),
                PItem(SfString("x-empty-header")),
                PItem(SfString("x-example")),
                PItem(SfString("x-example-dict"), Param("sf", SfBoolean(true)))
              ),
              params = ListMap(
                Token("keyid")   -> SfString("test-key-a"),
                Token("alg")     -> SfString("rsa-pss-sha512"),
                Token("created") -> SfInt("1402170695"),
                Token("expires") -> SfInt("1402170995")
              )
            )
        )
      )
    )

    /**
     * https://datatracker.ietf.org/doc/html/rfc9421#appendix-B.2.1
     */
    val `appendix-B.2.1` =
      rfc8792single("""sig-b21=();created=1618884473\
			  |      ;keyid="test-key-rsa-pss";nonce="b3k2pp5k7z-50gnwp.yemd"""")

    assertEquals(
      sfDictionary.parseAll(`appendix-B.2.1`),
      RA(
        ListMap[Token, Parameterized](
          Token("sig-b21") ->
            IList(
              items = List.empty,
              params = ListMap(
                Token("created") -> SfInt("1618884473"),
                Token("keyid")   -> SfString("test-key-rsa-pss"),
                Token("nonce")   -> SfString("b3k2pp5k7z-50gnwp.yemd")
              )
            )
        )
      )
    )

    val `appendix-B.2.2` =
      rfc8792single("""sig-b22=("@authority" "content-digest" \
                      |  "@query-param";name="Pet");created=1618884473\
                      |  ;keyid="test-key-rsa-pss";tag="header-example"""")

    assertEquals(
      sfDictionary.parseAll(`appendix-B.2.2`),
      RA(
        ListMap[Token, Parameterized](
          Token("sig-b22") ->
            IList(
              items = List(
                PItem(SfString("@authority")),
                PItem(SfString("content-digest")),
                PItem(SfString("@query-param"), Param("name", SfString("Pet")))
              ),
              params = ListMap(
                Token("created") -> SfInt("1618884473"),
                Token("keyid")   -> SfString("test-key-rsa-pss"),
                Token("tag")     -> SfString("header-example")
              )
            )
        )
      )
    )

  }

}
