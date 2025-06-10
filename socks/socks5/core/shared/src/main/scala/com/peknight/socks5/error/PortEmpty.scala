package com.peknight.socks5.error

import com.peknight.socks.error.StreamEmpty

object PortEmpty extends StreamEmpty:
  override def lowPriorityMessage: Option[String] = Some("port empty")
end PortEmpty
