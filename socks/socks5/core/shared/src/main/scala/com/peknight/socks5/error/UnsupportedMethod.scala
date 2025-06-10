package com.peknight.socks5.error

import com.peknight.socks.error.SocksError
import com.peknight.socks5.auth.Method

case class UnsupportedMethod(method: Method) extends SocksError:
  override def lowPriorityMessage: Option[String] = Some(s"unsupported authentication method: $method")
end UnsupportedMethod
