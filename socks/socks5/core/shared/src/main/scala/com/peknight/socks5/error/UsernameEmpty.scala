package com.peknight.socks5.error

import com.peknight.socks.error.StreamEmpty

object UsernameEmpty extends StreamEmpty:
  override def lowPriorityMessage: Option[String] = Some("username empty")
end UsernameEmpty
