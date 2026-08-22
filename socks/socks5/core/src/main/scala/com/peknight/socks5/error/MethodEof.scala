package com.peknight.socks5.error

import com.peknight.socks.error.StreamEof

object MethodEof extends StreamEof:
  override def lowPriorityMessage: Option[String] = Some("unexpected eof reading method")
end MethodEof
