package com.twitter.finagle.postgresql

import com.twitter.io.Buf

object Types {

  sealed trait Format
  object Format {
    case object Text extends Format
    case object Binary extends Format
  }

  case class Oid(value: Long)
  case class AttributeId(value: Short)

  case class FieldDescription(
    name: String,
    tableOid: Option[Oid],
    tableAttributeId: Option[AttributeId],
    dataType: Oid,
    dataTypeSize: Short, // negative means variable length
    typeModifier: Int, // meaning is type-specific
    format: Format
  )

  // portal and statement naming
  sealed trait Name
  object Name {
    case object Unnamed extends Name
    case class Named(value: String) extends Name {
      require(value.length > 0, "named prepared statement cannot be empty")
    }
  }

  sealed trait WireValue
  object WireValue {
    case object Null extends WireValue
    case class Value(buf: Buf) extends WireValue
  }

  case class PgArrayDim(size: Int, lowerBound: Int)
  case class PgArray(
    dimensions: Int,
    dataOffset: Int, // 0 means no null values,
    elemType: Oid,
    arrayDims: IndexedSeq[PgArrayDim],
    data: IndexedSeq[WireValue]
  )

  sealed trait Timestamp
  object Timestamp {
    case object NegInfinity extends Timestamp
    case object Infinity extends Timestamp
    case class Micros(offset: Long) extends Timestamp
  }

  sealed trait NumericSign
  object NumericSign {
    case object Positive extends NumericSign
    case object Negative extends NumericSign
    case object NaN extends NumericSign
    case object Infinity extends NumericSign
    case object NegInfinity extends NumericSign
  }
  case class Numeric(
    weight: Short, // unsigned?
    sign: NumericSign,
    displayScale: Int, // unsigned short
    digits: Seq[Short] // NumericDigit
  )

  /**
   * Postgres Inet type wrapper.
   *
   * Postgres Inet type (https://www.postgresql.org/docs/current/datatype-net-types.html#DATATYPE-INET)
   * is a tuple made of an address and a subnet (or "network mask").
   *
   * @param ipAddress the IpAddress part, e.g.: 192.168.0.1
   * @param netmask the netmask, or number of bits to consider in `ipAddress`.
   *                This is 0 to 32 for IPv4 and 0 to 128 for IPv6.
   *                This is an unsigned byte value, so using a `Short`.
   */
  case class Inet(ipAddress: java.net.InetAddress, netmask: Short)

}
