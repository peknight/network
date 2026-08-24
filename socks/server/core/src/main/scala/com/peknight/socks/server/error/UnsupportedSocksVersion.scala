package com.peknight.socks.server.error

case class UnsupportedSocksVersion(version: Byte) extends UnsupportedVersion:
  override def lowPriorityMessage: Option[String] = Some(s"unsupported socks version: $version")
end UnsupportedSocksVersion
