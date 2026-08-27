package com.peknight.socks5.auth.password

import com.peknight.socks5.error.AuthFailure

sealed trait Status derives CanEqual:
  def code: Byte
  def success: Boolean
end Status
object Status:
  def apply(code: Byte): Status =
    code match
      case 0x00 => Success
      case _ => Failure(code)
  case object Success extends Status:
    def code: Byte = 0x00
    def success: Boolean = true
  end Success
  case class Failure(code: Byte) extends Status with AuthFailure
  object Failure:
    def code: Byte = 0xFF.toByte
    val default: Failure = Failure(code)
  end Failure
end Status