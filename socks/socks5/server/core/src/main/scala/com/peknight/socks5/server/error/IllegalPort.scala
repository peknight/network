package com.peknight.socks5.server.error

import scodec.bits.ByteVector

case class IllegalPort(port: Int) extends Socks5ServerError:
  override def lowPriorityMessage: Option[String] = Some(s"illegal port $port")
end IllegalPort
