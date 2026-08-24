package com.peknight.socks5.server.error

import com.peknight.socks.error.StreamEof

object Ipv6AddressEof extends Socks5ServerError with StreamEof:
  override def lowPriorityMessage: Option[String] = Some("unexpected eof reading ipv6 address")
end Ipv6AddressEof
