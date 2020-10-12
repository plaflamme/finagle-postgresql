package com.twitter.finagle.postgresql.transport

import java.nio.ByteBuffer
import java.nio.ByteOrder

import com.twitter.finagle.postgresql.PropertiesSpec
import com.twitter.finagle.postgresql.Types.Format
import com.twitter.finagle.postgresql.Types.Name
import com.twitter.finagle.postgresql.Types.WireValue
import com.twitter.io.Buf
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.specs2.mutable.Specification

class PgBufSpec extends Specification with PropertiesSpec {

  case class UInt(bits: Int)
  object UInt {
    def apply(l: Long): UInt = UInt((l & 0xFFFFFFFF).toInt)
  }
  implicit val arbUInt: Arbitrary[UInt] =
    Arbitrary(Gen.chooseNum(0, Int.MaxValue.toLong * 2).map(UInt(_)))

  implicit val asciiString: Arbitrary[String] =
    Arbitrary(Gen.listOf(Gen.choose(32.toChar, 126.toChar)).map(_.mkString))

  "PgBuf" should {

    def expectedBytes[T](value: T)(expect: (ByteBuffer, T) => ByteBuffer): Array[Byte] = {
      val bb = expect(ByteBuffer.allocate(1024).order(ByteOrder.BIG_ENDIAN), value)
      bb.array().slice(bb.arrayOffset(), bb.position())
    }

    def writeFragment[T: Arbitrary](name: String)
                                   (write: (PgBuf.Writer, T) => PgBuf.Writer)
                                   (expect: (ByteBuffer, T) => ByteBuffer) = {
      s"write $name" in prop { value: T =>
        val bufWrite = write(PgBuf.writer, value).build
        Buf.ByteArray.Owned.extract(bufWrite) must_== expectedBytes(value)(expect)
      }
    }

    def readFragment[T: Arbitrary](name: String)
                                  (read: PgBuf.Reader => T)
                                  (expect: (ByteBuffer, T) => ByteBuffer) = {

      s"read $name" in prop { value: T =>
        read(PgBuf.reader(Buf.ByteArray.Owned(expectedBytes(value)(expect)))) must_== value
      }
    }

    def fragments[T: Arbitrary](name: String)
                              (write: (PgBuf.Writer, T) => PgBuf.Writer)
                              (read: PgBuf.Reader => T)
                              (expect: (ByteBuffer, T) => ByteBuffer) = {
      writeFragment[T](name)(write)(expect)
      readFragment[T](name)(read)(expect)

      s"round trip $name" in prop { value: T =>
        read(PgBuf.reader(write(PgBuf.writer, value).build)) must_== value
      }
    }

    fragments[Byte]("byte")(_.byte(_))(_.byte())(_.put(_))
    fragments[Int]("int")(_.int(_))(_.int())(_.putInt(_))
    fragments[UInt]("unsigned int")((w,uint) => w.unsignedInt(uint.bits))(r => UInt(r.unsignedInt()))((b,uint) => b.putInt(uint.bits))
    fragments[String]("cstring")(_.cstring(_))(_.cstring()) { (bb, str) =>
      bb.put(str.getBytes("UTF8"))
      bb.put(0.toByte)
    }(asciiString) // C-style strings only
    fragments[Buf]("buf")(_.buf(_))(_.remainingBuf()) { (bb, buf) =>
      bb.put(Buf.ByteBuffer.Shared.extract(buf))
    }
    fragments[Buf]("framed buf")(_.framedBuf(_))(_.framedBuf()) { (bb, buf) =>
      val value = Buf.ByteArray.Shared.extract(buf)
      bb.putInt(value.length)
      bb.put(value)
    }
    fragments[WireValue]("wire value")(_.value(_))(_.value()) { (bb, value) =>
      value match {
        case WireValue.Null => bb.putInt(-1)
        case WireValue.Value(buf) => {
          val value = Buf.ByteArray.Shared.extract(buf)
          bb.putInt(value.length)
          bb.put(value)
        }
      }
    }
    fragments[Format]("format")(_.format(_))(_.format()) { (bb, format) =>
      format match {
        case Format.Text => bb.putShort(0)
        case Format.Binary => bb.putShort(1)
      }
    }
    fragments[List[Int]]("foreach")(_.foreach(_)(_.int(_)))(_.collect(_.int()).toList) { (bb, xs) =>
      bb.putShort(xs.length.toShort)
      xs.foreach(v => bb.putInt(v))
      bb
    }

    "writer" should {
      writeFragment[Buf]("framed")((w, buf) => w.framed(inner => inner.buf(buf).build)) { (bb, buf) =>
        val value = Buf.ByteArray.Shared.extract(buf)
        bb.putInt(value.length + 4)
        bb.put(value)
      }
      writeFragment[Name]("name")(_.name(_)) { (bb, name) =>
        name match {
          case Name.Unnamed => bb.put(Array(0.toByte))
          case Name.Named(name) => {
            bb.put(name.getBytes("UTF-8"))
            bb.put(0.toByte)
          }
        }
      }
      writeFragment[List[Int]]("write foreachUnframed")(_.foreachUnframed(_)(_.int(_))) { (bb, xs) =>
        xs.foreach(v => bb.putInt(v))
        bb
      }

      "opt" in {
        PgBuf.writer.opt[Int](None)(_.int(_)).build.isEmpty must beTrue
        PgBuf.writer.opt[Int](Some(1))(_.int(_)).build.isEmpty must beFalse
      }
    }
  }
}
