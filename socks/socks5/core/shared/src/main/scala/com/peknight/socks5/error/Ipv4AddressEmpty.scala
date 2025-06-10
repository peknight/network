package com.peknight.socks5.error

import com.peknight.socks.error.StreamEmpty

object Ipv4AddressEmpty extends StreamEmpty:
  override def lowPriorityMessage: Option[String] = Some("ipv4 address empty")
end Ipv4AddressEmpty
