package com.peknight.socks5.server.error

import scodec.bits.ByteVector

case class IllegalDomainName(domainName: String) extends Socks5ServerError:
  override def lowPriorityMessage: Option[String] = Some(s"illegal domain name $domainName")
end IllegalDomainName
