package com.peknight.socks

import com.comcast.ip4s.GenSocketAddress

case class Connection[F[_]](address: GenSocketAddress, peerAddress: GenSocketAddress, endOfInput: F[Unit],
                            endOfOutput: F[Unit])
