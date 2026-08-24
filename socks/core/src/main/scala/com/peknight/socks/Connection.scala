package com.peknight.socks

import com.comcast.ip4s.GenSocketAddress

case class Connection(address: GenSocketAddress, peerAddress: GenSocketAddress)
