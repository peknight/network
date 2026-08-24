package com.peknight.socks5.server.error

import scodec.bits.ByteVector

case class IllegalIpv4Address(bytes: ByteVector) extends Socks5ServerError:
  override def lowPriorityMessage: Option[String] = Some(s"illegal ipv4 address ${bytes.toHex}")
end IllegalIpv4Address
