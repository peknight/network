package com.peknight.socks5.error

import com.peknight.socks.error.StreamEof

object Ipv4AddressEof extends StreamEof:
  override def lowPriorityMessage: Option[String] = Some("unexpected eof reading ipv4 address")
end Ipv4AddressEof
