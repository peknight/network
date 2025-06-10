package com.peknight.socks5.error

import com.peknight.socks.error.StreamEmpty

object MethodEmpty extends StreamEmpty:
  override def lowPriorityMessage: Option[String] = Some("method empty")
end MethodEmpty
