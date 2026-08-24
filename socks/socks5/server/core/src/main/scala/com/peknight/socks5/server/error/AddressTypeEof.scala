package com.peknight.socks5.server.error

import com.peknight.socks.error.StreamEof

object AddressTypeEof extends Socks5ServerError with StreamEof:
  override def lowPriorityMessage: Option[String] = Some("unexpected eof reading address type")
end AddressTypeEof
