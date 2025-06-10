package com.peknight.socks5.error

import com.peknight.socks.error.StreamEmpty

object PasswordVersionEmpty extends StreamEmpty:
  override def lowPriorityMessage: Option[String] = Some("password version empty")
end PasswordVersionEmpty
