package com.peknight.socks5.error

import com.peknight.socks.error.StreamEmpty

object DomainNameEmpty extends StreamEmpty:
  override def lowPriorityMessage: Option[String] = Some("domain name empty")
end DomainNameEmpty
