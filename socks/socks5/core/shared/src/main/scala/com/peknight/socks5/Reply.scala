package com.peknight.socks5

import com.peknight.error.Error
import com.peknight.socks.error.SocksError
import com.peknight.socks5.error.{UnsupportedAddressType, UnsupportedCommand}

sealed trait Reply:
  def code: Byte
  def success: Boolean
end Reply
object Reply:
  case object Succeeded extends Reply:
    def code: Byte = 0x00
    def success: Boolean = true
  case object GeneralSocksServerFailure extends Reply with SocksError:
    def code: Byte = 0x01
    override def lowPriorityMessage: Option[String] = Some("general SOCKS server failure")
  end GeneralSocksServerFailure
  case object ConnectionNotAllowedByRuleset extends Reply with SocksError:
    def code: Byte = 0x02
    override def lowPriorityMessage: Option[String] = Some("connection not allowed by ruleset")
  end ConnectionNotAllowedByRuleset
  case object NetworkUnreachable extends Reply with SocksError:
    def code: Byte = 0x03
    override protected def lowPriorityMessage: Option[String] = Some("Network unreachable")
  end NetworkUnreachable
  case object HostUnreachable extends Reply with SocksError:
    def code: Byte = 0x04
    override protected def lowPriorityMessage: Option[String] = Some("Host unreachable")
  end HostUnreachable
  case object ConnectionRefused extends Reply with SocksError:
    def code: Byte = 0x05
    override protected def lowPriorityMessage: Option[String] = Some("Connection refused")
  end ConnectionRefused
  case object TTLExpired extends Reply with SocksError:
    def code: Byte = 0x06
    override protected def lowPriorityMessage: Option[String] = Some("TTL expired")
  end TTLExpired
  case object CommandNotSupported extends Reply with SocksError:
    def code: Byte = 0x07
    override protected def lowPriorityMessage: Option[String] = Some("Command not supported")
  end CommandNotSupported
  case object AddressTypeNotSupported extends Reply with SocksError:
    def code: Byte = 0x08
    override protected def lowPriorityMessage: Option[String] = Some("Address type not supported")
  end AddressTypeNotSupported
  case class Unassigned(code: Byte) extends Reply with SocksError:
    require {
      val c = code & 0xFF
      c >= 0x09 && c <= 0xFF
    }
    override protected def lowPriorityMessage: Option[String] = Some("unassigned")
  end Unassigned
  def apply(byte: Byte): Reply = byte & 0XFF match
    case 0x00 => Succeeded
    case 0x01 => GeneralSocksServerFailure
    case 0x02 => ConnectionNotAllowedByRuleset
    case 0x03 => NetworkUnreachable
    case 0x04 => HostUnreachable
    case 0x05 => ConnectionRefused
    case 0x06 => TTLExpired
    case 0x07 => CommandNotSupported
    case 0x08 => AddressTypeNotSupported
    case code => Unassigned(code.toByte)
  def apply(value: Int): Reply = apply(value.toByte)

  def fromError[E](error: E): Reply =
    Error(error) match
      case _: UnsupportedCommand => CommandNotSupported
      case _: UnsupportedAddressType => AddressTypeNotSupported
      case _ => GeneralSocksServerFailure
end Reply