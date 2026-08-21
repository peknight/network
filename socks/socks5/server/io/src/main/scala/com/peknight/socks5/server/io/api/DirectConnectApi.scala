package com.peknight.socks5.server.io.api

import cats.effect.{Async, Resource}
import cats.syntax.applicative.*
import cats.syntax.applicativeError.*
import cats.syntax.functor.*
import com.comcast.ip4s.SocketAddress
import com.peknight.socks5.server.api.ConnectApi.ResourceConnectApi
import com.peknight.socks5.{Response, State}
import fs2.io.net.Network
import fs2.{Pipe, Stream}

class DirectConnectApi[F[_]: {Async, Network}, Auth] extends ResourceConnectApi[F, Auth]:
  def connect(state: State.Requested[Auth]): F[(Response, Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])])] =
    Network[F].connect(SocketAddress(state.request.address, state.request.port))
      .allocated
      .attempt
      .map {
        case Right((socket, release)) =>
          (Response.succeeded(socket.address), Resource.make[F, (Pipe[F, Byte, Unit], Stream[F, Byte])](
            (socket.writes, socket.reads).pure[F])(_ => release))
        case Left(error) => (Response.fromError(error), Resource.pure((_.drain, Stream.empty)))
      }
end DirectConnectApi
