package com.peknight.socks5.auth.password

enum PasswordVersion(val code: Byte):
  case version1 extends PasswordVersion(0x01)
end PasswordVersion
