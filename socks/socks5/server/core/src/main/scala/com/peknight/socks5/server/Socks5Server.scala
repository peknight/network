package com.peknight.socks5.server

import cats.effect.{Async, Resource}
import com.comcast.ip4s.{GenSocketAddress, SocketAddress}
import com.peknight.cats.instances.eitherT.given
import com.peknight.socks.Connection
import com.peknight.socks5.server.api.ServerApi
import com.peknight.socks5.server.state.ServerPullState
import com.peknight.socks5.state.State.Initial
import fs2.io.net.{Network, ServerSocket, SocketOption}
import fs2.{Pull, Stream}

import java.nio.charset.{Charset, StandardCharsets}
import java.time.LocalDateTime

trait Socks5Server[F[_], Auth, ConnectState, BindState, UDPAssociateState](using Charset)(using Async[F]):
  def api: ServerApi[F, Auth, ConnectState, BindState, UDPAssociateState]
  def bind: Resource[F, ServerSocket[F]]
  def resource: Resource[F, Stream[F, Nothing]] =
    bind.map(serverSocket => serverSocket.accept.map(socket => ServerPullState(api)
      .run((Initial(Connection(socket.address, socket.peerAddress, socket.endOfInput, socket.endOfOutput)),
        socket.reads))
      .as(())
      .stream
      .onFinalize(Async[F].delay(println(s"${LocalDateTime.now} server resource stream finalized")))
      .through(socket.writes)
      .onFinalize(Async[F].delay(println(s"${LocalDateTime.now} server resource writes finalized")))
      .attempt
      .drain
    ).parJoinUnbounded)
  def serve: Stream[F, Nothing] = Stream.resource(resource).flatten
end Socks5Server
object Socks5Server:
  private case class Socks5Server[F[_], Auth, ConnectState, BindState, UDPAssociateState](
    api: ServerApi[F, Auth, ConnectState, BindState, UDPAssociateState],
    address: GenSocketAddress = SocketAddress.Wildcard,
    options: List[SocketOption] = Nil,
    charset: Charset = StandardCharsets.UTF_8
  )(using Async[F], Network[F])
    extends com.peknight.socks5.server.Socks5Server[F, Auth, ConnectState, BindState, UDPAssociateState](using charset):
    def bind: Resource[F, ServerSocket[F]] = Network[F].bind(address, options)
  end Socks5Server
  def apply[F[_], Auth, ConnectState, BindState, UDPAssociateState](
    api: ServerApi[F, Auth, ConnectState, BindState, UDPAssociateState],
    address: GenSocketAddress = SocketAddress.Wildcard,
    options: List[SocketOption] = Nil, charset: Charset = StandardCharsets.UTF_8
  )(using Async[F], Network[F])
  : com.peknight.socks5.server.Socks5Server[F, Auth, ConnectState, BindState, UDPAssociateState] =
    Socks5Server(api, address, options)
end Socks5Server
