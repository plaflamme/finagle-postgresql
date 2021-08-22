import java.io.File
import scala.io.Source

object BuiltinTypeGenerator {

  // "int2vector" => "int2_vector"
  // "_bool" => "bool_array"
  // "_int2vector" => "int2_vector_array"
  private[this] def snakify(n: String): String =
    n.replaceFirst("^(.*)(vector|range)$", "$1_$2")
      .replaceFirst("^_(.*)$", "$1_array")

  // copied from com.twitter.conversions.StringOps.toPascalCase
  def toPascalCase(name: String): String = {
    def loop(x: List[Char]): List[Char] = (x: @unchecked) match {
      case '_' :: '_' :: rest => loop('_' :: rest)
      case '_' :: c :: rest => Character.toUpperCase(c) :: loop(rest)
      case '_' :: Nil => Nil
      case c :: rest => c :: loop(rest)
      case Nil => Nil
    }

    if (name == null) {
      ""
    } else {
      loop('_' :: name.toList).mkString
    }
  }

  // some generate names conflict with symbols, so we special case them here
  private[this] val Symbols = Set("Oid", "PgType")

  case class PgTypeEntry(
    oid: Long, // unsigned int
    array_type_oid: Option[Long],
    typname: String,
    typcategory: Char,
    typelem: Option[String],
    descr: Option[String],
  ) {

    def snakeIdent = snakify(typname)

    def ident = {
      val id = toPascalCase(snakeIdent)
      if (!Symbols(id)) id
      else {
        s"${id}Type"
      }
    }
  }

  case class PgTypeRegistry(types: List[PgTypeEntry]) {
    def byName(n: String) = types.find(_.typname == n)
    def byOid(oid: Long) = types.find(_.oid == oid)

    // returns another registry that includes the implicit array types
    def expand: PgTypeRegistry = {
      val expanded = types
        .flatMap { pgType =>
          pgType
            .array_type_oid
            .flatMap { oid =>
              byOid(oid) match {
                case Some(_) => None // already defined
                case None =>
                  // generate the implicit _array type
                  val array = PgTypeEntry(
                    oid = oid,
                    array_type_oid = None,
                    typname = s"_${pgType.typname}",
                    typcategory = 'A',
                    typelem = Some(pgType.typname),
                    descr = Some(s"Array of ${pgType.typname}")
                  )
                  Some(array)
              }
            }
        }
      PgTypeRegistry((types ++ expanded).sortBy(_.oid))
    }
  }

  def parsePgType(pgTypeFile: File): PgTypeRegistry = {
    val content = {
      val src = Source.fromFile(pgTypeFile, "UTF8")
      val r = src.mkString
      src.close()
      r
    }

    val types = DatParser.parse(content)
      .map { dat =>
        PgTypeEntry(
          oid = dat("oid")(_.toLong),
          array_type_oid = dat.opt("array_type_oid")(_.toLong),
          typname = dat.str("typname"),
          typcategory = dat("typcategory")(_.charAt(0)),
          typelem = dat.opt("typelem")(identity),
          descr = dat.opt("descr")(identity)
        )
      }

    PgTypeRegistry(types).expand
  }

  def header =
    """// Autogenerated file, do not edit.
      |// See BuiltinTypeGenerator
      |package com.twitter.finagle.postgresql.types
      |
      |import com.twitter.finagle.postgresql.Types.Oid
      |
      |case class PgType(name: String, oid: Oid, kind: Kind)
      |
      |object PgType {
      |
      |  /**
      |   * Lookup a type by its Oid.
      |   *
      |   * @return Some if such a type exists, None otherwise.
      |   */
      |  def byOid(oid: Oid): Option[PgType] =
      |    pgTypeByOid.get(oid)
      |
      |  /**
      |   * Find the corresponding one-dimensional array type for the specified element type.
      |   *
      |   * For example, passing `PgType.Int4` to this function would return `Some(PgType.Int4Vector)`.
      |   *
      |   * @return the corresponding array type, None if no such type exists.
      |   */
      |  def arrayOf(elementType: PgType): Option[PgType] =
      |    pgArrayOidByElementType.get(elementType)
      |
      |""".stripMargin

  def footer =
    """
      |}
      |""".stripMargin

  def indent(s: String, level: Int = 1) = {
    val prefix = Iterator.continually("  ").take(level).mkString
    s.split("\n")
      .map(s => s"$prefix$s")
      .mkString("\n")
  }

  def byOid(reg: PgTypeRegistry): String = {
    val patterns = reg.types
      .map { pgType =>
        s"Oid(${pgType.oid}) -> ${pgType.ident}"
      }
      .mkString(",\n")

    s"""val pgTypeByOid: Map[Oid, PgType] = Map(
      |${indent(patterns, 2)}
      |)
      |""".stripMargin
  }

  def arrayOf(reg: PgTypeRegistry): String = {
    val patterns = for {
      elementType <- reg.types
      arrayOid <- elementType.array_type_oid.toList
      arrayType <- reg.byOid(arrayOid).toList
    } yield s"${elementType.ident} -> ${arrayType.ident}"

    s"""val pgArrayOidByElementType: Map[PgType, PgType] = Map(
       |${indent(patterns.mkString(",\n"), 2)}
       |)
       |""".stripMargin
  }

  def typeVals(reg: PgTypeRegistry) =
    reg.types
      .map { pgType =>
        val kind = pgType.typcategory match {
          case 'A' => s"Kind.Array(${reg.byName(pgType.typelem.get).get.ident})"
          case 'P' => "Kind.Pseudo"
          case _ => "Kind.Base"
        }

        s"""/**
           | * ${pgType.descr.getOrElse("")}
           | */
           |lazy val ${pgType.ident} : PgType = PgType("${pgType.typname}", Oid(${pgType.oid}), $kind)
           |""".stripMargin
      }
      .mkString("\n")

  def body(reg: PgTypeRegistry): String =
    byOid(reg) ++ arrayOf(reg) ++ typeVals(reg)

  def generate(pgTypeFile: File) =
    header ++ indent(body(parsePgType(pgTypeFile))) ++ footer
}
