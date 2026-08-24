package com.peknight.socks5.server.error

import com.peknight.socks.server.error.UnsupportedVersion

case class UnsupportedPasswordVersion(version: Byte) extends Socks5ServerError with UnsupportedVersion:
  override def lowPriorityMessage: Option[String] = Some(s"unsupported username/password version: $version")
end UnsupportedPasswordVersion
