package com.peknight.socks5.server.error

case class UnsupportedCommand(command: Byte) extends Socks5ServerError:
  override def lowPriorityMessage: Option[String] = Some(s"unsupported commend: $command")
end UnsupportedCommand
