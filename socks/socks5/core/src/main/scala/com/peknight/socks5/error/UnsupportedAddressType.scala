package com.peknight.socks5.error

case class UnsupportedAddressType(addressType: Byte) extends Socks5Error:
  override def lowPriorityMessage: Option[String] = Some(s"unsupported address type: $addressType")
end UnsupportedAddressType
