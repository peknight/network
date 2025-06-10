package com.peknight.socks5.error

import com.peknight.socks.error.StreamEmpty

object ReservedEmpty extends StreamEmpty:
  override def lowPriorityMessage: Option[String] = Some("reserved empty")
end ReservedEmpty
