package com.peknight.socks5.error

trait AuthFailure extends Socks5Error:
  override def lowPriorityMessage: Option[String] = Some("authentication failed")
end AuthFailure
