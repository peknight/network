package com.peknight.socks5.error

import com.peknight.socks.error.SocksError
import scodec.bits.ByteVector

case class IllegalIpv6Address(bytes: ByteVector) extends SocksError:
  override def lowPriorityMessage: Option[String] = Some(s"illegal ipv6 address ${bytes.toHex}")
end IllegalIpv6Address
