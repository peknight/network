package com.peknight.socks5.error

import com.peknight.socks.error.StreamEof

object AddressTypeEof extends Socks5Error with StreamEof:
  override def lowPriorityMessage: Option[String] = Some("unexpected eof reading address type")
end AddressTypeEof
