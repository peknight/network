package com.peknight.socks5.server.error

import com.peknight.socks.error.StreamEof

object Ipv4AddressEof extends Socks5ServerError with StreamEof:
  override def lowPriorityMessage: Option[String] = Some("unexpected eof reading ipv4 address")
end Ipv4AddressEof
