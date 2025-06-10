package com.peknight.socks5.error

import com.peknight.socks.error.SocksError

case class UnsupportedAddressType(addressType: Byte) extends SocksError:
  override def lowPriorityMessage: Option[String] = Some(s"unsupported address type: $addressType")
end UnsupportedAddressType
