package com.peknight.socks5

enum Command(val code: Byte):
  case CONNECT extends Command(0x01)
  case BIND extends Command(0x02)
  case UDP_ASSOCIATE extends Command(0x03)
end Command
