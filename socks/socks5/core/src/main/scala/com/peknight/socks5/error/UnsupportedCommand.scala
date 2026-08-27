package com.peknight.socks5.error

case class UnsupportedCommand(command: Byte) extends Socks5Error:
  override def lowPriorityMessage: Option[String] = Some(s"unsupported commend: $command")
end UnsupportedCommand
