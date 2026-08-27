package com.peknight.socks5.error

import scodec.bits.ByteVector

case class IllegalPort(port: Int) extends Socks5Error:
  override def lowPriorityMessage: Option[String] = Some(s"illegal port $port")
end IllegalPort
