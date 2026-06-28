package com.peknight.socks5.error

import com.peknight.socks.error.StreamEmpty

object CommandEmpty extends StreamEmpty:
  override def lowPriorityMessage: Option[String] = Some("command empty")
end CommandEmpty
