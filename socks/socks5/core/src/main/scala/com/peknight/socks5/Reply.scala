package com.peknight.socks5

import com.peknight.socks5.error.Socks5Error

sealed trait Reply derives CanEqual:
  def code: Byte
  def success: Boolean
end Reply
object Reply:
  case object Succeeded extends Reply:
    def code: Byte = 0x00
    def success: Boolean = true
  sealed trait Failed extends Reply with Socks5Error
  case object GeneralSocksServerFailure extends Reply with Failed:
    def code: Byte = 0x01
    override def lowPriorityMessage: Option[String] = Some("general SOCKS server failure")
  end GeneralSocksServerFailure
  case object ConnectionNotAllowedByRuleset extends Reply with Failed:
    def code: Byte = 0x02
    override def lowPriorityMessage: Option[String] = Some("connection not allowed by ruleset")
  end ConnectionNotAllowedByRuleset
  case object NetworkUnreachable extends Reply with Failed:
    def code: Byte = 0x03
    override protected def lowPriorityMessage: Option[String] = Some("Network unreachable")
  end NetworkUnreachable
  case object HostUnreachable extends Reply with Failed:
    def code: Byte = 0x04
    override protected def lowPriorityMessage: Option[String] = Some("Host unreachable")
  end HostUnreachable
  case object ConnectionRefused extends Reply with Failed:
    def code: Byte = 0x05
    override protected def lowPriorityMessage: Option[String] = Some("Connection refused")
  end ConnectionRefused
  case object TTLExpired extends Reply with Failed:
    def code: Byte = 0x06
    override protected def lowPriorityMessage: Option[String] = Some("TTL expired")
  end TTLExpired
  case object CommandNotSupported extends Reply with Failed:
    def code: Byte = 0x07
    override protected def lowPriorityMessage: Option[String] = Some("Command not supported")
  end CommandNotSupported
  case object AddressTypeNotSupported extends Reply with Failed:
    def code: Byte = 0x08
    override protected def lowPriorityMessage: Option[String] = Some("Address type not supported")
  end AddressTypeNotSupported
  case class Unassigned(code: Byte) extends Reply with Failed:
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
end Reply