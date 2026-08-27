package com.peknight.socks5.client.error

import com.peknight.socks.error.StreamEof

object MethodEof extends Socks5ClientError with StreamEof:
  override def lowPriorityMessage: Option[String] = Some("unexpected eof reading method")
end MethodEof
