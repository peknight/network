package com.peknight.socks5.error

import com.peknight.socks.error.StreamEmpty

object Socks5VersionEmpty extends StreamEmpty:
  override def lowPriorityMessage: Option[String] = Some("socks5 version empty")
end Socks5VersionEmpty
