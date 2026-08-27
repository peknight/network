package com.peknight.socks5.error

import scodec.bits.ByteVector

case class IllegalIpv4Address(bytes: ByteVector) extends Socks5Error:
  override def lowPriorityMessage: Option[String] = Some(s"illegal ipv4 address ${bytes.toHex}")
end IllegalIpv4Address
