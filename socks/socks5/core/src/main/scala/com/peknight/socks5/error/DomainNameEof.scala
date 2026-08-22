package com.peknight.socks5.error

import com.peknight.socks.error.StreamEof

object DomainNameEof extends StreamEof:
  override def lowPriorityMessage: Option[String] = Some("unexpected eof reading domain name")
end DomainNameEof
