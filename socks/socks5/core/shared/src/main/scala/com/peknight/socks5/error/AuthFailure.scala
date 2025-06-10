package com.peknight.socks5.error

import com.peknight.socks.error.SocksError

trait AuthFailure extends SocksError:
  override def lowPriorityMessage: Option[String] = Some("authentication failed")
end AuthFailure
