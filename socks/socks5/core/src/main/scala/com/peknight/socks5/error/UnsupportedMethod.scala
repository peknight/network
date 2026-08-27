package com.peknight.socks5.error

import com.peknight.socks5.auth.Method

case class UnsupportedMethod(method: Method) extends Socks5Error:
  override def lowPriorityMessage: Option[String] = Some(s"unsupported authentication method: $method")
end UnsupportedMethod
