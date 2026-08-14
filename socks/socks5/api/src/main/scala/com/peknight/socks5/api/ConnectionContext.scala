package com.peknight.socks5.api

import com.comcast.ip4s.GenSocketAddress

case class ConnectionContext(address: GenSocketAddress, peerAddress: GenSocketAddress)
