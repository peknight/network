package com.peknight.socks5.client.io

import cats.effect.{Async, Resource}
import com.comcast.ip4s.GenSocketAddress
import com.peknight.cats.instances.eitherT.given
import com.peknight.socks.Connection
import com.peknight.socks5.client.api.ClientApi
import com.peknight.socks5.client.state.ClientPullState
import com.peknight.socks5.state.State.Initial
import fs2.Stream
import fs2.io.net.{Network, Socket, SocketOption}

import java.nio.charset.{Charset, StandardCharsets}
import java.time.LocalDateTime

trait Socks5Client[F[_], Auth, ConnectState, BindState, UDPAssociateState](using Charset)(using Async[F]):
  def api: ClientApi[F, Auth, ConnectState, BindState, UDPAssociateState]
  def connect: Resource[F, Socket[F]]
  def resource: Resource[F, Stream[F, Nothing]] =
    connect.map(socket => ClientPullState(api)
      .run((Initial(Connection(socket.address, socket.peerAddress)), socket.reads))
      .as(())
      .stream
      .onFinalize(Async[F].delay(println(s"${LocalDateTime.now} client resource stream finalized")))
      .through(socket.writes)
      .onFinalize(Async[F].delay(println(s"${LocalDateTime.now} client resource writes finalized")))
      .attempt
      .drain
    )
  def run: Stream[F, Nothing] = Stream.resource(resource).flatten
end Socks5Client
object Socks5Client:
  private case class Socks5Client[F[_], Auth, ConnectState, BindState, UDPAssociateState](
    api: ClientApi[F, Auth, ConnectState, BindState, UDPAssociateState],
    address: GenSocketAddress,
    options: List[SocketOption] = Nil,
    charset: Charset = StandardCharsets.UTF_8
  )(using Async[F], Network[F])
    extends com.peknight.socks5.client.io.Socks5Client[F, Auth, ConnectState, BindState, UDPAssociateState](using charset):
    def connect: Resource[F, Socket[F]] = Network[F].connect(address, options)
  end Socks5Client
  def apply[F[_], Auth, ConnectState, BindState, UDPAssociateState](
    api: ClientApi[F, Auth, ConnectState, BindState, UDPAssociateState],
    address: GenSocketAddress,
    options: List[SocketOption] = Nil, charset: Charset = StandardCharsets.UTF_8
  )(using Async[F], Network[F])
  : com.peknight.socks5.client.io.Socks5Client[F, Auth, ConnectState, BindState, UDPAssociateState] =
    Socks5Client(api, address, options)
end Socks5Client
