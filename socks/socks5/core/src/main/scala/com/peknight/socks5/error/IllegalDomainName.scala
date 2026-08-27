package com.peknight.socks5.error

import scodec.bits.ByteVector

case class IllegalDomainName(domainName: String) extends Socks5Error:
  override def lowPriorityMessage: Option[String] = Some(s"illegal domain name $domainName")
end IllegalDomainName
