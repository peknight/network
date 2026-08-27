package com.peknight.socks5.client.error

import com.peknight.socks.error.StreamEof

object ReplyEof extends Socks5ClientError with StreamEof:
  override def lowPriorityMessage: Option[String] = Some("unexpected eof reading reply")
end ReplyEof
