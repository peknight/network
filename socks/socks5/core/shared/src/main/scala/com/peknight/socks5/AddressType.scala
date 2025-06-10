package com.peknight.socks5

import com.comcast.ip4s
import com.comcast.ip4s.Host

enum AddressType(val code: Byte) derives CanEqual:
  case Ipv4Address extends AddressType(0x01)
  case DomainName extends AddressType(0x03)
  case Ipv6Address extends AddressType(0x04)
end AddressType
object AddressType:
  def fromHost(host: Host): AddressType =
    host match
      case address: ip4s.Ipv4Address => Ipv4Address
      case address: ip4s.Ipv6Address => Ipv6Address
      case _ => DomainName
end AddressType
