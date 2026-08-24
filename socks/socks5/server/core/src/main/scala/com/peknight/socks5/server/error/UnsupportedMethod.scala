package com.peknight.socks5.server.error

import com.peknight.socks5.auth.Method

case class UnsupportedMethod(method: Method) extends Socks5ServerError:
  override def lowPriorityMessage: Option[String] = Some(s"unsupported authentication method: $method")
end UnsupportedMethod
