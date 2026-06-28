package com.peknight.socks5.error

import com.peknight.socks.error.UnsupportedVersion

case class UnsupportedPasswordVersion(version: Byte) extends UnsupportedVersion:
  override def lowPriorityMessage: Option[String] = Some(s"unsupported username/password version: $version")
end UnsupportedPasswordVersion
