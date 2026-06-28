package com.peknight.socks.error

import com.peknight.socks.error.SocksError

trait UnsupportedVersion extends SocksError:
  def version: Byte
end UnsupportedVersion
