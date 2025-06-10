package com.peknight.socks5.error

import com.peknight.socks.error.SocksError
import scodec.bits.ByteVector

case class IllegalPort(port: Int) extends SocksError:
  override def lowPriorityMessage: Option[String] = Some(s"illegal port $port")
end IllegalPort
