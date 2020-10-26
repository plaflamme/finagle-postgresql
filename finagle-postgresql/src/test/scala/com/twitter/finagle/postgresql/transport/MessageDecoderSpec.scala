package com.twitter.finagle.postgresql.transport

import java.nio.{ByteBuffer, ByteOrder}

import com.twitter.finagle.postgresql.BackendMessage.{AuthenticationCleartextPassword, AuthenticationGSS, AuthenticationGSSContinue, AuthenticationKerberosV5, AuthenticationMD5Password, AuthenticationMessage, AuthenticationOk, AuthenticationSASL, AuthenticationSASLContinue, AuthenticationSASLFinal, AuthenticationSCMCredential, AuthenticationSSPI, BindComplete, CommandComplete, EmptyQueryResponse, FailedTx, Field, InTx, NoData, NoTx, Parameter, ParameterDescription, ParseComplete, PortalSuspended, ReadyForQuery, TxState}
import com.twitter.finagle.postgresql.Types.{AttributeId, Format, Oid, WireValue}
import com.twitter.finagle.postgresql.{BackendMessage, PropertiesSpec}
import com.twitter.io.Buf
import org.scalacheck.{Arbitrary, Gen}
import org.specs2.mutable.Specification

class MessageDecoderSpec extends Specification with PropertiesSpec {

  def mkBuf(capacity: Int = 32768)(f: ByteBuffer => ByteBuffer): Buf = {
    val bb = ByteBuffer.allocate(capacity).order(ByteOrder.BIG_ENDIAN)
    f(bb)
    bb.flip()
    Buf.ByteBuffer.Owned(bb)
  }
  def cstring(s: String) = s.getBytes("UTF8") :+ 0x00.toByte

  def unsignedInt(v: Long) = (v & 0xFFFFFFFFL).toInt

  def fieldByte(field: Field): Byte =
    field match {
      case Field.LocalizedSeverity => 'S'
      case Field.Severity => 'V'
      case Field.Code => 'C'
      case Field.Message => 'M'
      case Field.Detail => 'D'
      case Field.Hint => 'H'
      case Field.Position => 'P'
      case Field.InternalPosition => 'p'
      case Field.InternalQuery => 'q'
      case Field.Where => 'W'
      case Field.Schema => 's'
      case Field.Table => 't'
      case Field.Column => 'c'
      case Field.DataType => 'd'
      case Field.Constraint => 'n'
      case Field.File => 'F'
      case Field.Line => 'L'
      case Field.Routine => 'R'
      case Field.Unknown(c) => c.toByte
    }

  implicit lazy val arbCommandComplete: Arbitrary[CommandComplete] = Arbitrary(genAsciiString.map(_.value).map(CommandComplete))

  lazy val genAuthenticationMessage: Gen[AuthenticationMessage] =
    Gen.oneOf(
      Gen.const(AuthenticationOk),
      Gen.const(AuthenticationKerberosV5),
      Gen.const(AuthenticationCleartextPassword),
      Gen.const(AuthenticationSCMCredential),
      Gen.containerOfN[Array, Byte](4, Arbitrary.arbitrary[Byte]).map(Buf.ByteArray.Owned(_)).map(AuthenticationMD5Password),
      Gen.const(AuthenticationGSS),
      Gen.const(AuthenticationSSPI),
      genBuf.map(AuthenticationGSSContinue),
      genAsciiString.map(str => AuthenticationSASL(str.value)),
      genBuf.map(AuthenticationSASLContinue),
      genBuf.map(AuthenticationSASLFinal),
    )
  implicit lazy val arbAuthenticationMessage: Arbitrary[AuthenticationMessage] = Arbitrary(genAuthenticationMessage)

  lazy val genTxState: Gen[TxState] = Gen.oneOf(NoTx, InTx, FailedTx)
  implicit lazy val arbReadyForQuery: Arbitrary[ReadyForQuery] = Arbitrary(genTxState.map(ReadyForQuery))

  implicit lazy val arbParameterDescription: Arbitrary[ParameterDescription] = Arbitrary(Arbitrary.arbitrary[IndexedSeq[Oid]].map(ParameterDescription))

  def decodeFragment[M <: BackendMessage: Arbitrary](dec: MessageDecoder[M])(toPacket: M => Packet) = {
    "decode packet body correctly" in prop { msg: M =>
      dec.decode(PgBuf.reader(toPacket(msg).body)).asScala must beSuccessfulTry(msg)
    }
    "decode packet correctly" in prop { msg: M =>
      MessageDecoder.fromPacket(toPacket(msg)).asScala must beSuccessfulTry(msg)
    }
  }

  def singleton[M <: BackendMessage](key: Byte, msg: M) = {
    "decode packet correctly" in {
      MessageDecoder.fromPacket(Packet(Some(key), Buf.Empty)).asScala must beSuccessfulTry(msg)
    }
  }

  "MessageDecoder" should {
    "ErrorResponse" should decodeFragment(MessageDecoder.errorResponseDecoder) { msg =>
      Packet(
        cmd = Some('E'),
        body = mkBuf() { bb =>
          msg.values.foreach { case(field, value) =>
            bb.put(fieldByte(field)).put(cstring(value))
          }
          bb.put(0.toByte)
        }
      )
    }

    "NoticeResponse" should decodeFragment(MessageDecoder.noticeResponseDecoder) { msg =>
      Packet(
        cmd = Some('N'),
        body = mkBuf() { bb =>
          msg.values.foreach { case(field, value) =>
            bb.put(fieldByte(field)).put(cstring(value))
          }
          bb.put(0.toByte)
        }
      )
    }

    "BackendKeyData" should decodeFragment(MessageDecoder.backendKeyDataDecoder) { msg =>
      Packet(
        cmd = Some('K'),
        body = mkBuf() { bb =>
          bb.putInt(msg.pid)
          bb.putInt(msg.secret)
        }
      )
    }

    "CommandComplete" should decodeFragment(MessageDecoder.commandCompleteDecoder) { msg =>
      Packet(
        cmd = Some('C'),
        body = mkBuf() { bb =>
          bb.put(cstring(msg.commandTag))
        }
      )
    }

    "AuthenticationMessage" should decodeFragment(MessageDecoder.authenticationMessageDecoder) { msg =>
      Packet(
        cmd = Some('R'),
        body = mkBuf() { bb =>
          msg match {
            case AuthenticationOk => bb.putInt(0)
            case AuthenticationKerberosV5 => bb.putInt(2)
            case AuthenticationCleartextPassword => bb.putInt(3)
            case AuthenticationMD5Password(buf) => bb.putInt(5).put(Buf.ByteBuffer.Shared.extract(buf))
            case AuthenticationSCMCredential => bb.putInt(6)
            case AuthenticationGSS => bb.putInt(7)
            case AuthenticationGSSContinue(buf) => bb.putInt(8).put(Buf.ByteBuffer.Shared.extract(buf))
            case AuthenticationSSPI => bb.putInt(9)
            case AuthenticationSASL(m) => bb.putInt(10).put(cstring(m))
            case AuthenticationSASLContinue(buf) => bb.putInt(11).put(Buf.ByteBuffer.Shared.extract(buf))
            case AuthenticationSASLFinal(buf) => bb.putInt(12).put(Buf.ByteBuffer.Shared.extract(buf))
          }
        }
      )
    }

    "ParameterStatus" should decodeFragment(MessageDecoder.parameterStatusDecoder) { msg =>
      Packet(
        cmd = Some('S'),
        body = mkBuf() { bb =>
          val name = msg.key match {
            case Parameter.ServerVersion => "server_version"
            case Parameter.ServerEncoding => "server_encoding"
            case Parameter.ClientEncoding => "client_encoding"
            case Parameter.ApplicationName => "application_name"
            case Parameter.IsSuperUser => "is_superuser"
            case Parameter.SessionAuthorization => "session_authorization"
            case Parameter.DateStyle => "DateStyle"
            case Parameter.IntervalStyle => "IntervalStyle"
            case Parameter.TimeZone => "TimeZone"
            case Parameter.IntegerDateTimes => "integer_datetimes"
            case Parameter.StandardConformingStrings => "standard_conforming_strings"
            case Parameter.Other(n) => n
          }
          bb.put(cstring(name)).put(cstring(msg.value))
        }
      )
    }

    "ReaderForQuery" should decodeFragment(MessageDecoder.readyForQueryDecoder) { msg =>
      Packet(
        cmd = Some('Z'),
        body = mkBuf() { bb =>
          val tx = msg.state match {
            case NoTx => 'I'
            case InTx => 'T'
            case FailedTx => 'F'
          }
          bb.put(tx.toByte)
        }
      )
    }

    "RowDescription" should decodeFragment(MessageDecoder.rowDescriptionDecoder) { msg =>
      Packet(
        cmd = Some('T'),
        body = mkBuf() { bb =>
          bb.putShort(msg.rowFields.size.toShort)
          msg.rowFields.foreach { f =>
            bb.put(cstring(f.name))
            f.tableOid match {
              case None => bb.putInt(0)
              case Some(oid) => bb.putInt(unsignedInt(oid.value))
            }
            f.tableAttributeId match {
              case None => bb.putShort(0)
              case Some(AttributeId(value)) => bb.putShort(value.toShort)
            }
            bb.putInt(unsignedInt(f.dataType.value))
            bb.putShort(f.dataTypeSize)
            bb.putInt(f.typeModifier)
            f.format match {
              case Format.Text => bb.putShort(0)
              case Format.Binary => bb.putShort(1)
            }
          }
          bb
        }
      )
    }

    "DataRow" should decodeFragment(MessageDecoder.dataRowDecoder) { msg =>
      Packet(
        cmd = Some('D'),
        body = mkBuf() { bb =>
          bb.putShort(msg.values.size.toShort)
          msg.values.foreach {
            case WireValue.Null => bb.putInt(-1)
            case WireValue.Value(v) => bb.putInt(v.length).put(Buf.ByteBuffer.Shared.extract(v))
          }
          bb
        }
      )
    }

    "ParameterDescription" should decodeFragment(MessageDecoder.parameterDescriptionDecoder) { msg =>
      Packet(
        cmd = Some('t'),
        body = mkBuf() { bb =>
          bb.putShort(msg.parameters.size.toShort)
          msg.parameters.foreach { oid => bb.putInt(unsignedInt(oid.value)) }
          bb
        }
      )
    }

    "ParseComplete" should singleton('1', ParseComplete)
    "BindComplete" should singleton('2', BindComplete)
    "EmptyQueryResponse" should singleton('I', EmptyQueryResponse)
    "BindComplete" should singleton('n', NoData)
    "BindComplete" should singleton('s', PortalSuspended)
  }

}
