package com.peknight.socks5.client.io

import cats.effect.{Async, Resource}
import com.peknight.cats.instances.eitherT.given
import com.peknight.socks.Connection
import com.peknight.socks5.client.api.ClientApi
import com.peknight.socks5.client.state.ClientPullState
import com.peknight.socks5.state.State.Initial
import fs2.io.net.Socket
import fs2.{Pipe, Stream}

import java.nio.charset.Charset

trait Socks5Client[F[_], Auth, ConnectState, BindState, UDPAssociateState](using Charset)(using Async[F]):
  def api: ClientApi[F, Auth, ConnectState, BindState, UDPAssociateState]
  def connect: Resource[F, Socket[F]]
  def run: Pipe[F, Byte, Byte] =
    in => Stream.resource(connect).flatMap(socket => ClientPullState(api)
      .run((Initial(Connection(socket.address, socket.peerAddress)), socket.reads))
      .as(())
      .stream
      .through(socket.writes)
      .attempt
      .drain
    )
end Socks5Client
