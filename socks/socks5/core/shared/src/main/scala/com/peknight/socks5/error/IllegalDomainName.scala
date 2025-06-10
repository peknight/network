package com.peknight.socks5.error

import com.peknight.socks.error.SocksError
import scodec.bits.ByteVector

case class IllegalDomainName(domainName: String) extends SocksError:
  override def lowPriorityMessage: Option[String] = Some(s"illegal domain name $domainName")
end IllegalDomainName
