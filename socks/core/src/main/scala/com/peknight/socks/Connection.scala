package com.peknight.socks

import com.comcast.ip4s.GenSocketAddress

case class Connection[F[_]](address: GenSocketAddress, peerAddress: GenSocketAddress, endOfInput: F[Unit],
                            endOfOutput: F[Unit])
object Connection:
  def apply[F[_]](socket: fs2.io.net.Socket[F]): Connection[F] =
    Connection(socket.address, socket.peerAddress, socket.endOfInput, socket.endOfOutput)
end Connection
