package com.peknight.socks5.error

import com.peknight.socks.error.StreamEof

object Socks5VersionEof extends Socks5Error with StreamEof:
  override def lowPriorityMessage: Option[String] = Some("unexpected eof reading socks5 version")
end Socks5VersionEof
