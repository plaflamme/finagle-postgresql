package com.twitter.finagle.postgresql

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.twitter.finagle.postgresql.BackendMessage.CommandTag
import com.twitter.finagle.postgresql.BackendMessage.DataRow
import com.twitter.finagle.postgresql.BackendMessage.Field
import com.twitter.finagle.postgresql.BackendMessage.RowDescription
import com.twitter.finagle.postgresql.FrontendMessage.DescriptionTarget
import com.twitter.finagle.postgresql.Types.FieldDescription
import com.twitter.finagle.postgresql.Types.Format
import com.twitter.finagle.postgresql.Types.Inet
import com.twitter.finagle.postgresql.Types.Name
import com.twitter.finagle.postgresql.Types.Numeric
import com.twitter.finagle.postgresql.Types.NumericSign
import com.twitter.finagle.postgresql.Types.Oid
import com.twitter.finagle.postgresql.Types.PgArray
import com.twitter.finagle.postgresql.Types.PgArrayDim
import com.twitter.finagle.postgresql.Types.Timestamp
import com.twitter.finagle.postgresql.Types.WireValue
import com.twitter.finagle.postgresql.types.Json
import com.twitter.finagle.postgresql.types.PgDate
import com.twitter.finagle.postgresql.types.PgNumeric
import com.twitter.finagle.postgresql.types.PgTime
import com.twitter.io.Buf
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.specs2.ScalaCheck
import org.specs2.matcher.describe.ComparisonResult
import org.specs2.matcher.describe.Diffable
import org.typelevel.jawn.ast.DeferLong
import org.typelevel.jawn.ast.DeferNum
import org.typelevel.jawn.ast.DoubleNum
import org.typelevel.jawn.ast.FastRenderer
import org.typelevel.jawn.ast.JArray
import org.typelevel.jawn.ast.JFalse
import org.typelevel.jawn.ast.JNull
import org.typelevel.jawn.ast.JObject
import org.typelevel.jawn.ast.JParser
import org.typelevel.jawn.ast.JString
import org.typelevel.jawn.ast.JTrue
import org.typelevel.jawn.ast.JValue
import org.typelevel.jawn.ast.LongNum

trait PropertiesSpec extends ScalaCheck {

  import ArbitraryJson._

  case class AsciiString(value: String)
  lazy val genAsciiChar: Gen[Char] = Gen.choose(32.toChar, 126.toChar)
  lazy val genAsciiString: Gen[AsciiString] = Gen.listOf(genAsciiChar).map(_.mkString).map(AsciiString)
  implicit lazy val arbAsciiString: Arbitrary[AsciiString] = Arbitrary(genAsciiString)

  case class JsonString(value: String)
  lazy val genJsonString: Gen[JsonString] = Arbitrary.arbitrary[JValue].map(jv => JsonString(jv.render(FastRenderer)))
  implicit lazy val arbJsonString: Arbitrary[JsonString] = Arbitrary(genJsonString)
  lazy val genJson: Gen[Json] = genJsonString.map(str => Json(Buf.Utf8(str.value), StandardCharsets.UTF_8))
  implicit lazy val arbJson: Arbitrary[Json] = Arbitrary(genJson)

  // TODO: Once we have actual data types, Gen.oneOf(...)
  implicit lazy val arbOid = Arbitrary(Gen.chooseNum(0, 0xffffffffL).map(Oid))

  val genParameter: Gen[BackendMessage.Parameter] = Gen.oneOf(
    BackendMessage.Parameter.ServerVersion,
    BackendMessage.Parameter.ServerEncoding,
    BackendMessage.Parameter.ClientEncoding,
    BackendMessage.Parameter.ApplicationName,
    BackendMessage.Parameter.IsSuperUser,
    BackendMessage.Parameter.SessionAuthorization,
    BackendMessage.Parameter.DateStyle,
    BackendMessage.Parameter.IntervalStyle,
    BackendMessage.Parameter.TimeZone,
    BackendMessage.Parameter.IntegerDateTimes,
    BackendMessage.Parameter.StandardConformingStrings,
    BackendMessage.Parameter.Other("other_param"),
  )
  implicit lazy val arbParam: Arbitrary[BackendMessage.ParameterStatus] = Arbitrary {
    for {
      param <- genParameter
      value <- Gen.alphaLowerStr.suchThat(_.nonEmpty)
    } yield BackendMessage.ParameterStatus(param, value)
  }
  implicit lazy val arbBackendKeyData: Arbitrary[BackendMessage.BackendKeyData] = Arbitrary {
    for {
      pid <- Arbitrary.arbitrary[Int]
      key <- Arbitrary.arbitrary[Int]
    } yield BackendMessage.BackendKeyData(pid, key)
  }

  val genCommandTag: Gen[CommandTag] =
    for {
      rows <- Gen.chooseNum(0, Int.MaxValue)
      tag <- Gen.oneOf(
        CommandTag.Insert(rows),
        CommandTag.Update(rows),
        CommandTag.Delete(rows),
        CommandTag.Select(rows),
        CommandTag.Move(rows),
        CommandTag.Fetch(rows),
        CommandTag.Other("SOME TAG"),
      )
    } yield tag
  implicit lazy val arbCommandTag: Arbitrary[CommandTag] = Arbitrary(genCommandTag)

  implicit lazy val arbFieldDescription: Arbitrary[FieldDescription] = Arbitrary {
    for {
      name <- Gen.alphaStr.suchThat(_.nonEmpty)
      dataType <- Arbitrary.arbitrary[Oid]
      dataTypeSize <- Gen.oneOf(1, 2, 4, 8, 16).map(_.toShort)
      format <- Gen.oneOf(Format.Text, Format.Binary)
    } yield FieldDescription(name, None, None, dataType, dataTypeSize, 0, format)
  }

  implicit lazy val arbNamed: Arbitrary[Name.Named] = Arbitrary(Gen.alphaLowerStr.suchThat(_.nonEmpty).map(Name.Named))
  implicit lazy val arbName: Arbitrary[Name] =
    Arbitrary(Gen.oneOf(Gen.const(Name.Unnamed), Arbitrary.arbitrary[Name.Named]))

  implicit lazy val arbRowDescription: Arbitrary[RowDescription] = Arbitrary {
    Gen.nonEmptyListOf(arbFieldDescription.arbitrary).map(l => RowDescription(l.toIndexedSeq))
  }

  lazy val genBuf: Gen[Buf] = Arbitrary.arbitrary[Array[Byte]].map(bytes => Buf.ByteArray.Owned(bytes))
  implicit lazy val arbBuf: Arbitrary[Buf] = Arbitrary(genBuf)

  // TODO: this will need to be dervied from the dataType when used in a DataRow
  lazy val genValue: Gen[WireValue] = arbBuf.arbitrary.map(b => WireValue.Value(b))

  implicit lazy val arbValue: Arbitrary[WireValue] = Arbitrary {
    // TODO: more weight on non-null
    Gen.oneOf(Gen.const(WireValue.Null), genValue)
  }

  // TODO: produce the appropriate bytes based on the field descriptors. Should also include nulls.
  def genRowData(rowDescription: RowDescription): Gen[DataRow] =
    Gen.containerOfN[IndexedSeq, WireValue](rowDescription.rowFields.size, arbValue.arbitrary)
      .map(DataRow)

  lazy val genDataRow: Gen[DataRow] = for {
    row <- Arbitrary.arbitrary[RowDescription]
    data <- genRowData(row)
  } yield data

  implicit lazy val arbDataRow: Arbitrary[DataRow] = Arbitrary(genDataRow)

  // A self-contained, valid result set, i.e.: the row field data match the field descriptors
  case class TestResultSet(desc: RowDescription, rows: List[DataRow])
  implicit lazy val arbTestResultSet: Arbitrary[TestResultSet] = Arbitrary {
    for {
      desc <- arbRowDescription.arbitrary
      rows <- Gen.listOf(genRowData(desc))
    } yield TestResultSet(desc, rows)
  }

  implicit lazy val arbTarget: Arbitrary[DescriptionTarget] =
    Arbitrary(Gen.oneOf(DescriptionTarget.PreparedStatement, DescriptionTarget.Portal))

  lazy val genField: Gen[Field] = Gen.oneOf(
    Field.Code,
    Field.Column,
    Field.Constraint,
    Field.DataType,
    Field.Detail,
    Field.File,
    Field.Hint,
    Field.InternalPosition,
    Field.InternalQuery,
    Field.Line,
    Field.LocalizedSeverity,
    Field.Message,
    Field.Position,
    Field.Routine,
    Field.Schema,
    Field.Severity,
    Field.Table,
    Field.Where,
    Field.Unknown('U') // TODO
  )

  lazy val fieldMap: Gen[Map[Field, String]] = for {
    nbValues <- Gen.chooseNum(0, 8)
    keys <- Gen.containerOfN[List, Field](nbValues, genField)
    values <- Gen.containerOfN[List, String](nbValues, genAsciiString.map(_.value))
  } yield (keys zip values).toMap

  implicit lazy val arbErrorResponse: Arbitrary[BackendMessage.ErrorResponse] =
    Arbitrary(fieldMap.map(BackendMessage.ErrorResponse))
  implicit lazy val arbNoticeResponse: Arbitrary[BackendMessage.NoticeResponse] =
    Arbitrary(fieldMap.map(BackendMessage.NoticeResponse))

  implicit lazy val arbFormat: Arbitrary[Format] =
    Arbitrary(Gen.oneOf(Format.Text, Format.Binary))

  /**
   * Diffable[Buf] to get a the actual Buf's bytes when printing.
   */
  implicit lazy val bufDiffable: Diffable[Buf] = new Diffable[Buf] {
    override def diff(actual: Buf, expected: Buf): ComparisonResult =
      new ComparisonResult {
        def hex(buf: Buf): String = {
          val h = Buf.ByteArray.Owned.extract(buf).map(s => f"$s%02X").mkString
          s"0x$h"
        }

        override def identical: Boolean = Buf.equals(actual, expected)

        override def render: String = s"${hex(actual)} != ${hex(expected)}"
      }
  }

  /**
   * Diffable[Json] because json equivalence isn't trivially done by comparing strings
   */
  implicit lazy val jsonDiffable: Diffable[Json] = new Diffable[Json] {
    override def diff(actual: Json, expected: Json): ComparisonResult = {
      val actualJson = JParser.parseFromString(actual.jsonString).get
      val expectedJson = JParser.parseFromString(expected.jsonString).get

      // Implment a custom equals check that delegates to BigDecimal for comparing numbers.
      // It's slower, but will not cause false negatives for some edge cases.
      def jsEq(l: JValue, r: JValue): Boolean =
        l match {
          case JNull => r.isNull
          case JTrue | JFalse => l.asBoolean == r.asBoolean
          case JString(ls) => ls == r.asString
          case LongNum(ln) => ln == r.asLong
          case DoubleNum(dn) => BigDecimal(dn) == r.asBigDecimal
          case DeferLong(dn) => BigDecimal(dn) == r.asBigDecimal
          case DeferNum(dn) => BigDecimal(dn) == r.asBigDecimal
          case JArray(arr) => arr.zipWithIndex.forall { case (jv, i) => jsEq(jv, r.get(i)) }
          case JObject(obj) => obj.forall { case (key, jv) => jsEq(jv, r.get(key)) }
        }

      new ComparisonResult {
        override def identical: Boolean = jsEq(actualJson, expectedJson)

        override def render: String = s"$actualJson != $expectedJson"
      }
    }
  }

  lazy val genArrayDim: Gen[PgArrayDim] = Gen.chooseNum(1, 100).map { size =>
    PgArrayDim(size, 1)
  }
  lazy val genArray: Gen[PgArray] =
    for {
      dimensions <- Gen.chooseNum(1, 4)
      oid <- arbOid.arbitrary
      dims <- Gen.containerOfN[IndexedSeq, PgArrayDim](dimensions, genArrayDim)
      data <- Gen.containerOfN[IndexedSeq, WireValue](dims.map(_.size).sum, genValue)
    } yield PgArray(
      dimensions = dimensions,
      dataOffset = 0,
      elemType = oid,
      arrayDims = dims,
      data = data,
    )

  implicit lazy val arbPgArray: Arbitrary[PgArray] = Arbitrary(genArray)

  lazy val genInstant: Gen[Instant] = for {
    secs <- Gen.chooseNum(PgTime.Min.getEpochSecond, PgTime.Max.getEpochSecond)
    nanos <- Gen.chooseNum(PgTime.Min.getNano, PgTime.Max.getNano)
  } yield Instant.ofEpochSecond(secs, nanos.toLong).truncatedTo(ChronoUnit.MICROS)
  implicit val arbInstant: Arbitrary[Instant] = Arbitrary(genInstant)

  lazy val genMicros: Gen[Timestamp.Micros] =
    genInstant.map(i => i.getEpochSecond * 1000000 + i.getNano / 1000).map(Timestamp.Micros)
  lazy val genTimestamp: Gen[Timestamp] =
    Gen.frequency(99 -> genMicros, 1 -> Gen.oneOf(Timestamp.NegInfinity, Timestamp.Infinity))
  implicit lazy val arbTimestamp = Arbitrary(genTimestamp)

  lazy val genLocalDate: Gen[LocalDate] = for {
    days <- Gen.chooseNum(-2451545, 2145031948)
  } yield PgDate.Epoch.plusDays(days.toLong)
  implicit val arbLocalDate: Arbitrary[LocalDate] = Arbitrary(genLocalDate)

  lazy val genNumericSign: Gen[NumericSign] = Gen.oneOf(
    NumericSign.Positive,
    NumericSign.Negative,
    NumericSign.NaN,
    NumericSign.NegInfinity,
    NumericSign.Infinity
  )
  implicit lazy val arbNumericSign: Arbitrary[NumericSign] = Arbitrary(genNumericSign)
  lazy val genNumeric: Gen[Numeric] = implicitly[Arbitrary[BigDecimal]].arbitrary.map(PgNumeric.bigDecimalToNumeric)
  implicit lazy val arbNumeric: Arbitrary[Numeric] = Arbitrary(genNumeric)

  lazy val genIp: Gen[java.net.InetAddress] = for {
    size <- Gen.oneOf(4, 16)
    addr <- Gen.buildableOfN[Array[Byte], Byte](size, Arbitrary.arbitrary[Byte])
  } yield java.net.InetAddress.getByAddress(addr)
  implicit lazy val arbIp: Arbitrary[java.net.InetAddress] = Arbitrary(genIp)

  lazy val genInet: Gen[Inet] = for {
    ip <- genIp
    mask <- ip match {
      case _: java.net.Inet4Address => Gen.choose(0, 32)
      case _: java.net.Inet6Address => Gen.choose(0, 128)
    }
  } yield Inet(ip, mask.toShort)
  implicit lazy val arbInet: Arbitrary[Inet] = Arbitrary(genInet)

  implicit lazy val arbBackendCopyData: Arbitrary[BackendMessage.CopyData] =
    Arbitrary(genBuf.map(BackendMessage.CopyData))
  implicit lazy val arbFrontendCopyData: Arbitrary[FrontendMessage.CopyData] =
    Arbitrary(genBuf.map(FrontendMessage.CopyData))

}
