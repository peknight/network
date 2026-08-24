package com.peknight.socks5.server.error

case class UnsupportedReserved(reserved: Byte) extends Socks5ServerError:
  override def lowPriorityMessage: Option[String] = Some(s"unsupported reserved: $reserved")
end UnsupportedReserved
