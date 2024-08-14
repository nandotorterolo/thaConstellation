package cli

trait UserInputParser[T] {

  def parse(input: String): Either[String, T]

}
object UserInputParser {

  def apply[A](implicit instance: UserInputParser[A]): UserInputParser[A] = instance

  private[cli] implicit val parseString: UserInputParser[String] = (s: String) => Either.cond(s.nonEmpty, s, "Empty Input")

  private[cli] implicit val parseInt: UserInputParser[Int] = (s: String) => s.toIntOption.toRight("Not an Int")

  private[cli] implicit val parseDouble: UserInputParser[Double] = (s: String) => s.toDoubleOption.toRight("Not a Double")

}
