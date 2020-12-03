package com.twitter.finagle.postgresql

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date
import java.util.UUID

import com.twitter.finagle.postgresql.types.ValueWrites
import com.twitter.io.Buf

trait FinaglePostgresqlEncoders {
  this: FinaglePostgresqlContext[_] =>

  type Encoder[T] = FinaglePostgresqlEncoder[T]

  case class FinaglePostgresqlEncoder[T](writes: ValueWrites[T]) extends BaseEncoder[T] {
    override def apply(index: Index, value: T, row: PrepareRow): PrepareRow =
      row :+ Parameter(value)(writes)
  }

  def encoder[T](implicit writes: ValueWrites[T]): FinaglePostgresqlEncoder[T] =
    FinaglePostgresqlEncoder(writes)

  override implicit def optionEncoder[T](implicit d: Encoder[T]): Encoder[Option[T]] =
    encoder(ValueWrites.optionWrites(d.writes))

  override implicit def mappedEncoder[I, O](implicit mapped: MappedEncoding[I, O], enc: Encoder[O]): Encoder[I] =
    encoder(ValueWrites.by(mapped.f)(enc.writes))

  implicit val stringEncoder: Encoder[String] = encoder[String]
  implicit val bigDecimalEncoder: Encoder[BigDecimal] = encoder[BigDecimal]
  implicit val booleanEncoder: Encoder[Boolean] = encoder[Boolean]
  implicit val byteEncoder: Encoder[Byte] = encoder[Byte]
  implicit val shortEncoder: Encoder[Short] = encoder[Short]
  implicit val intEncoder: Encoder[Int] = encoder[Int]
  implicit val longEncoder: Encoder[Long] = encoder[Long]
  implicit val floatEncoder: Encoder[Float] = encoder[Float]
  implicit val doubleEncoder: Encoder[Double] = encoder[Double]
  implicit val byteArrayEncoder: Encoder[Array[Byte]] =
    encoder(ValueWrites.by[Buf, Array[Byte]](bytes => Buf.ByteArray.Shared(bytes)))
  implicit val dateEncoder: Encoder[Date] =
    encoder(ValueWrites.by[java.time.Instant, Date](_.toInstant))
  implicit val localDateEncoder: Encoder[LocalDate] = encoder[java.time.LocalDate]
  implicit val localDateTimeEncoder: Encoder[LocalDateTime] =
    encoder(ValueWrites.by[java.time.Instant, LocalDateTime](_.toInstant(ZoneOffset.UTC)))
  implicit val uuidEncoder: Encoder[UUID] = encoder[UUID]
}
