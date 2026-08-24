package com.peknight.socks5.server.error

import scodec.bits.ByteVector

case class IllegalIpv6Address(bytes: ByteVector) extends Socks5ServerError:
  override def lowPriorityMessage: Option[String] = Some(s"illegal ipv6 address ${bytes.toHex}")
end IllegalIpv6Address
