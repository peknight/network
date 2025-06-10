package com.peknight.socks5.error

import com.peknight.socks.error.StreamEmpty

object PasswordEmpty extends StreamEmpty:
  override def lowPriorityMessage: Option[String] = Some("password empty")
end PasswordEmpty
