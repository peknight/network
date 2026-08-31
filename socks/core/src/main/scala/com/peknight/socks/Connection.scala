package com.peknight.socks

import com.comcast.ip4s.GenSocketAddress
import fs2.Pipe

case class Connection[F[_]](address: GenSocketAddress, peerAddress: GenSocketAddress,
                            writes: Pipe[F, Byte, Nothing], endOfInput: F[Unit], endOfOutput: F[Unit])
object Connection:
  def apply[F[_]](socket: fs2.io.net.Socket[F]): Connection[F] =
    Connection(socket.address, socket.peerAddress, socket.writes, socket.endOfInput, socket.endOfOutput)
end Connection