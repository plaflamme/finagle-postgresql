import scala.util.parsing.combinator.RegexParsers

case class Dat(values: Map[String, String]) {
  def apply[T](key: String)(f: String => T): T = f(values(key))
  def str[T](key: String): String = values(key)
  def opt[T](key: String)(f: String => T): Option[T] = values.get(key).map(f)
}
object DatParser extends RegexParsers {

  override def skipWhitespace = true

  private[this] sealed trait Token
  private[this] case object Ignore extends Token
  private[this] case class Ident(value: String) extends Token
  private[this] case class Lit(value: String) extends Token
  private[this] case object Arrow extends Token
  private[this] case object Comma extends Token
  private[this] case object Open extends Token
  private[this] case object Close extends Token
  private[this] case class Entry(values: Map[String, String]) extends Token

  private[this] def ignore =
    ("""#.*""".r | "[" | "]") ^^ { _ => Ignore }

  private[this] def ident: Parser[Ident] =
    "[a-zA-Z_][a-zA-Z0-9_]*".r ^^ { str => Ident(str) }

  private[this] def lit: Parser[Lit] =
    """'(?:[^\\']|\\')*'""".r ^^ { str =>
      Lit(str.substring(1, str.length - 1).replaceAllLiterally("\\", ""))
    }

  private[this] def symbol[T](key: String, value: T) = key ^^ (_ => value)
  private[this] def arrow = symbol("=>", Arrow)
  private[this] def comma = symbol(",", Comma)
  private[this] def open = symbol("{", Open)
  private[this] def close = symbol("}", Close)

  private[this] def tuple =
    (ident ~ arrow ~ lit) ^^ { case Ident(i) ~ _ ~ Lit(l) => i -> l }

  private[this] def entry: Parser[Entry] =
    open ~> repsep(tuple, comma) <~ (close ~ comma) ^^ { values => Entry(values.toMap) }

  private[this] def tokens: Parser[List[Token]] =
    phrase(rep(ignore | entry))

  private[this] def dat: Parser[List[Dat]] =
    tokens ^^ { tokens => tokens.collect { case Entry(values) => Dat(values) } }

  def parse(source: String): List[Dat] =
    parse(dat, source).get
}
