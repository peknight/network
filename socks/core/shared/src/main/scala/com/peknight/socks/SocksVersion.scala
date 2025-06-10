package com.peknight.socks

enum SocksVersion(val code: Byte):
  case socks5 extends SocksVersion(0x05)
end SocksVersion
