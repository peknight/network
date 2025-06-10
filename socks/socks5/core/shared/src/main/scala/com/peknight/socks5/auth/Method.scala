package com.peknight.socks5.auth

import com.peknight.socks.error.SocksError

sealed trait Method derives CanEqual:
  def code: Byte
  def acceptable: Boolean
  def authRequired: Boolean
end Method
object Method:

  sealed trait AcceptableMethod extends Method:
    def acceptable: Boolean = true
  end AcceptableMethod

  case object NoAuthenticationRequired extends AcceptableMethod:
    val code: Byte = 0x00
    def authRequired: Boolean = false
  end NoAuthenticationRequired

  sealed trait AuthRequiredMethod extends AcceptableMethod:
    def authRequired: Boolean = true
  end AuthRequiredMethod

  case object GSSAPI extends AuthRequiredMethod:
    val code: Byte = 0x01
  end GSSAPI
  case object UsernamePassword extends AuthRequiredMethod:
    val code: Byte = 0x02
  end UsernamePassword
  case class IANAAssigned(code: Byte) extends AuthRequiredMethod:
    require(code >= 0x03 && code <= 0x7F)
  end IANAAssigned
  case class PrivateMethod(code: Byte) extends AuthRequiredMethod:
    require {
      val c = code & 0xFF
      c >= 0x80 && c <= 0xFE
    }
  end PrivateMethod
  case object NoAcceptableMethod extends Method with SocksError:
    val code: Byte = 0xFF.toByte
    def acceptable: Boolean = false
    def authRequired: Boolean = false
    override def lowPriorityMessage: Option[String] = Some("no acceptable method")
  end NoAcceptableMethod

  def apply(byte: Byte): Method = byte & 0xFF match
    case 0x00 => NoAuthenticationRequired
    case 0x01 => GSSAPI
    case 0x02 => UsernamePassword
    case c if c >= 0x03 && c <= 0x7F => IANAAssigned(c.toByte)
    case c if c >= 0x80 && c <= 0xFE => PrivateMethod(c.toByte)
    case 0xFF => NoAcceptableMethod
  def apply(value: Int): Method = apply(value.toByte)
end Method
