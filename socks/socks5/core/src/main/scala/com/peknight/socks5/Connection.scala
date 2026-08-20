package com.peknight.socks5

import com.comcast.ip4s.GenSocketAddress

case class Connection(address: GenSocketAddress, peerAddress: GenSocketAddress)
