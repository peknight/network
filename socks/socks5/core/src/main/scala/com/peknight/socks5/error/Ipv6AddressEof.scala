package com.peknight.socks5.error

import com.peknight.socks.error.StreamEof

object Ipv6AddressEof extends StreamEof:
  override def lowPriorityMessage: Option[String] = Some("unexpected eof reading ipv6 address")
end Ipv6AddressEof
