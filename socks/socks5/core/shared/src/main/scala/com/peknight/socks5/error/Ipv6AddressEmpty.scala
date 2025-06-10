package com.peknight.socks5.error

import com.peknight.socks.error.StreamEmpty

object Ipv6AddressEmpty extends StreamEmpty:
  override def lowPriorityMessage: Option[String] = Some("ipv6 address empty")
end Ipv6AddressEmpty
