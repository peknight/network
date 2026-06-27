package com.peknight.socks5.error

import com.peknight.socks.error.SocksError

case class UnsupportedCommand(command: Byte) extends SocksError:
  override def lowPriorityMessage: Option[String] = Some(s"unsupported commend: $command")
end UnsupportedCommand
