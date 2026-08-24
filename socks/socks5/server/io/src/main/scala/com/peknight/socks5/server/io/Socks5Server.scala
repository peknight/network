package com.peknight.socks5.server.io

import cats.effect.Async
import com.comcast.ip4s.{GenSocketAddress, SocketAddress}
import com.peknight.cats.instances.eitherT.given
import com.peknight.socks.Connection
import com.peknight.socks5.server.api.Socks5ServerApi
import com.peknight.socks5.server.state.State.Initial
import com.peknight.socks5.server.state.state
import fs2.io.net.{Network, Socket, SocketOption}
import fs2.{Pull, Stream}

import java.nio.charset.{Charset, StandardCharsets}

trait Socks5Server[F[_], Auth, ConnectState, BindState, UDPAssociateState](using Charset)(using Async[F]):
  def api: Socks5ServerApi[F, Auth, ConnectState, BindState, UDPAssociateState]
  def bindAndAccept: Stream[F, Socket[F]]
  def serve: Stream[F, Unit] =
    bindAndAccept.map(socket => state(api)
      .run((Initial(Connection(socket.address, socket.peerAddress)), socket.reads))
      .as(())
      .stream
      .through(socket.writes)
      .attempt
      .drain
    ).parJoinUnbounded
end Socks5Server
object Socks5Server:
  private case class Socks5Server[F[_], Auth, ConnectState, BindState, UDPAssociateState](
    api: Socks5ServerApi[F, Auth, ConnectState, BindState, UDPAssociateState],
    address: GenSocketAddress = SocketAddress.Wildcard,
    options: List[SocketOption] = Nil,
    charset: Charset = StandardCharsets.UTF_8
  )(using Async[F], Network[F])
    extends com.peknight.socks5.server.io.Socks5Server[F, Auth, ConnectState, BindState, UDPAssociateState](using charset):
    def bindAndAccept: Stream[F, Socket[F]] = Network[F].bindAndAccept(address, options)
  end Socks5Server
  def apply[F[_], Auth, ConnectState, BindState, UDPAssociateState](
    api: Socks5ServerApi[F, Auth, ConnectState, BindState, UDPAssociateState],
    address: GenSocketAddress = SocketAddress.Wildcard,
    options: List[SocketOption] = Nil, charset: Charset = StandardCharsets.UTF_8)(using Async[F], Network[F])
  : com.peknight.socks5.server.io.Socks5Server[F, Auth, ConnectState, BindState, UDPAssociateState] =
    Socks5Server(api, address, options)
end Socks5Server
