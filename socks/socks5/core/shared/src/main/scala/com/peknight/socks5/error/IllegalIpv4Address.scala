package com.peknight.socks5.error

import com.peknight.socks.error.SocksError
import scodec.bits.ByteVector

case class IllegalIpv4Address(bytes: ByteVector) extends SocksError:
  override def lowPriorityMessage: Option[String] = Some(s"illegal ipv4 address ${bytes.toHex}")
end IllegalIpv4Address
