package com.peknight.socks5.server.error

import com.peknight.socks.error.StreamEof

object Socks5VersionEof extends Socks5ServerError with StreamEof:
  override def lowPriorityMessage: Option[String] = Some("unexpected eof reading socks5 version")
end Socks5VersionEof
