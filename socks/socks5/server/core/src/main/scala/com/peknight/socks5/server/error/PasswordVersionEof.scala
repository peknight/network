package com.peknight.socks5.server.error

import com.peknight.socks.error.StreamEof

object PasswordVersionEof extends Socks5ServerError with StreamEof:
  override def lowPriorityMessage: Option[String] = Some("unexpected eof reading password version")
end PasswordVersionEof
