package com.peknight.socks5.client.error

import com.peknight.socks.error.StreamEof

object StatusEof extends Socks5ClientError with StreamEof:
  override def lowPriorityMessage: Option[String] = Some("unexpected eof reading status")
end StatusEof
