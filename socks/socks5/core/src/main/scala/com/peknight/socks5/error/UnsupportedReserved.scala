package com.peknight.socks5.error

case class UnsupportedReserved(reserved: Byte) extends Socks5Error:
  override def lowPriorityMessage: Option[String] = Some(s"unsupported reserved: $reserved")
end UnsupportedReserved
