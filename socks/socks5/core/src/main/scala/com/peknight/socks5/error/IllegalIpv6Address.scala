package com.peknight.socks5.error

import scodec.bits.ByteVector

case class IllegalIpv6Address(bytes: ByteVector) extends Socks5Error:
  override def lowPriorityMessage: Option[String] = Some(s"illegal ipv6 address ${bytes.toHex}")
end IllegalIpv6Address
