package com.twitter.finagle.postgresql

import java.time.LocalDate
import java.util.Date
import java.util.UUID

import com.twitter.finagle.postgresql.types.ValueReads
import com.twitter.io.Buf

trait FinaglePostgresqlDecoders {
  this: FinaglePostgresqlContext[_] =>

  type Decoder[T] = FinaglePostgresqlDecoder[T]

  case class FinaglePostgresqlDecoder[T](reads: ValueReads[T]) extends BaseDecoder[T] {
    override def apply(index: Index, row: ResultRow): T =
      row.get(index)(reads)
  }

  override implicit def optionDecoder[T](implicit d: Decoder[T]): Decoder[Option[T]] =
    FinaglePostgresqlDecoder(ValueReads.optionReads(d.reads))

  override implicit def mappedDecoder[I, O](implicit mapped: MappedEncoding[I, O], decoder: Decoder[I]): Decoder[O] =
    FinaglePostgresqlDecoder(ValueReads.by(mapped.f)(decoder.reads))

  def decoder[T](implicit reads: ValueReads[T]): FinaglePostgresqlDecoder[T] =
    FinaglePostgresqlDecoder(reads)

  implicit val stringDecoder: Decoder[String] = decoder[String]
  implicit val bigDecimalDecoder: Decoder[BigDecimal] = decoder[BigDecimal]
  implicit val booleanDecoder: Decoder[Boolean] = decoder[Boolean]
  implicit val byteDecoder: Decoder[Byte] = decoder[Byte]
  implicit val shortDecoder: Decoder[Short] = decoder[Short]
  implicit val intDecoder: Decoder[Int] = decoder[Int]
  implicit val longDecoder: Decoder[Long] = decoder[Long]
  implicit val floatDecoder: Decoder[Float] = decoder[Float]
  implicit val doubleDecoder: Decoder[Double] = decoder[Double]
  implicit val byteArrayDecoder: Decoder[Array[Byte]] =
    decoder(ValueReads.by[Buf, Array[Byte]](b => Buf.ByteArray.Shared.extract(b)))
  implicit val dateDecoder: Decoder[Date] =
    decoder(ValueReads.by[java.time.Instant, Date](java.util.Date.from))
  implicit val localDateDecoder: Decoder[LocalDate] = decoder[java.time.LocalDate]
  implicit val uuidDecoder: Decoder[UUID] = decoder[UUID]
}
