package com.peknight.socks5.server.error

case class UnsupportedAddressType(addressType: Byte) extends Socks5ServerError:
  override def lowPriorityMessage: Option[String] = Some(s"unsupported address type: $addressType")
end UnsupportedAddressType
