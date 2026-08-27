package com.peknight.socks.error

trait UnsupportedVersion extends SocksError:
  def version: Byte
end UnsupportedVersion
