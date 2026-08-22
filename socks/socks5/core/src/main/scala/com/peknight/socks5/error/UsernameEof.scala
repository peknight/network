package com.peknight.socks5.error

import com.peknight.socks.error.StreamEof

object UsernameEof extends StreamEof:
  override def lowPriorityMessage: Option[String] = Some("unexpected eof reading username")
end UsernameEof
