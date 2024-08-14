import cats.data.EitherT
import cats.effect.std.Console
import cats.effect.Sync
import cats.implicits._
import fs2.io.file.Files
import fs2.io.file.Path
import fs2.Chunk

package object cli {

  sealed abstract class YesNo
  case object Yes extends YesNo
  case object No  extends YesNo

  /**
   * writes on console
   */
  def write[F[_]: Sync: Console](message: String): CommandT[F, Unit] =
    CommandT.liftF[F, Unit](Sync[F].defer(Console[F].println(message)))

  /**
   * Prompt user input
   */
  def read[F[_]: Sync: Console]: CommandT[F, String] =
    CommandT.liftF(
      Sync[F].defer(Console[F].print("> ").flatMap(_ => Console[F].readLine.map(_.trim)))
    )

  /**
   * read parameter
   * @param param para name
   * @param examples examples
   */
  def readParameter[F[_]: Sync: Console, T: UserInputParser](
      param: String,
      examples: List[String]
  ): CommandT[F, T] =
    readOptionalParameter[F, T](param, examples).untilDefinedM

  /**
   * read optional parameter
   * @param param name parameter
   * @param examples examples
   */
  def readOptionalParameter[F[_]: Sync: Console, T: UserInputParser](
      param: String,
      examples: List[String]
  ): CommandT[F, Option[T]] =
    (write[F](s"Enter $param parameter. (Ex: ${examples.mkString(", ")})") >>
      read[F]
        .flatMap {
          case "" => none[T].some.pure[CommandT[F, *]]
          case input =>
            EitherT
              .fromEither[CommandT[F, *]](UserInputParser[T].parse(input))
              .leftSemiflatTap(error => write(s"Invalid $param. Reason=$error input=$input"))
              .map(_.some)
              .toOption
              .value
        }).untilDefinedM

  /**
   * Prompts a user for an enumeration of inputs, and repeats the process until a valid input is received.
   * @param prompt The message to give to the user
   * @param choices A list of (lowercase) choices
   * @param default An optional default value to use if no value is provided.
   *                If the default is part of `choices`, it will be uppercased in the printed message
   * @return The user's selection
   */
  def readChoice[F[_]: Sync: Console](
      prompt: String
  )(choices: List[String], default: Option[String] = None): CommandT[F, String] = {
    val stringifiedChoices =
      choices
        .map(c => if (default.contains(c)) c.toUpperCase else c)
        .map(c => s" $c ")
        .mkString("[", "|", "]")
    (write[F](s"$prompt $stringifiedChoices") >> read[F])
      .map(_.trim)
      .flatMap(response =>
        if (response.isEmpty) {
          default.fold(write[F]("No input provided.").as(none[String]))(d => CommandT.liftF(d.some.pure[F]))
        } else if (choices.contains(response)) CommandT.liftF(response.some.pure[F])
        else write[F](s"Invalid input: $response").as(none[String])
      )
      .untilDefinedM
  }

  /**
   * Yes-or-No response, and run the corresponding ifYes/ifNo function.
   */
  def readYesNo[F[_]: Sync: Console, T](prompt: String, default: Option[YesNo] = None)(
      ifYes: => CommandT[F, T],
      ifNo: => CommandT[F, T]
  ): CommandT[F, T] =
    readChoice(prompt)(
      List("y", "n"),
      default.map {
        case Yes => "y"
        case No  => "n"
      }
    ).flatMap {
      case "y" => ifYes
      case "n" => ifNo
    }

  /**
   * Write content on given directory/file to disk
   */
  def writeFile[F[_]: Sync: Files: Console](
      directory: Path
  )(contents: Array[Byte])(fileName: String): CommandT[F, Unit] =
    CommandT.liftF[F, Unit] {
      val destination = directory / fileName
      Console[F].println(show"Writing to $destination") >>
        Files[F].createDirectories(directory) >>
        fs2.Stream
          .chunk(Chunk.array(contents))
          .through(Files[F].writeAll(destination))
          .compile
          .drain
    }

  /**
   * Read content from path
   * @param path path
   */
  def readFile[F[_]: Sync: Files](path: Path): CommandT[F, Vector[Byte]] =
    CommandT.liftF[F, Vector[Byte]] {
      Files[F].readAll(path).compile.toVector
    }

}
