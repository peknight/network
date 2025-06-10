package com.peknight.socks5.error

import com.peknight.socks.error.SocksError

case class UnsupportedReserved(reserved: Byte) extends SocksError:
  override def lowPriorityMessage: Option[String] = Some(s"unsupported reserved: $reserved")
end UnsupportedReserved
