package com.peknight.socks.server.error

trait UnsupportedVersion extends SocksServerError:
  def version: Byte
end UnsupportedVersion
