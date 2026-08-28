package com.peknight.socks5.server.api

import cats.Applicative
import cats.effect.{Async, MonadCancel, Resource}
import cats.syntax.applicative.*
import cats.syntax.applicativeError.*
import cats.syntax.functor.*
import com.comcast.ip4s.SocketAddress
import com.peknight.socks.Socket
import com.peknight.socks5.Response
import com.peknight.socks5.server.error.toResponse
import com.peknight.socks5.state.State.{Connected, Requested}
import fs2.io.net.Network

trait ConnectApi[F[_], Auth, ConnectState]:
  def connect(state: Requested[F, Auth]): F[(Response, ConnectState)]
  def tunnel(state: Connected[F, Auth, ConnectState]): Resource[F, Socket[F]]
end ConnectApi
object ConnectApi:
  trait ResourceConnectApi[F[_], Auth](using MonadCancel[F, ?])
    extends ConnectApi[F, Auth, Resource[F, Socket[F]]]:
    def tunnel(state: Connected[F, Auth, Resource[F, Socket[F]]]): Resource[F, Socket[F]] =
      state.state
  end ResourceConnectApi
  private class DirectConnectApi[F[_]: {Async, Network}, Auth] extends ResourceConnectApi[F, Auth]:
    def connect(state: Requested[F, Auth]): F[(Response, Resource[F, Socket[F]])] =
      Network[F].connect(SocketAddress(state.request.address, state.request.port))
        .allocated
        .attempt
        .map {
          case Right((socket, release)) =>
            (Response.succeeded(socket.address), Resource.make[F, Socket[F]](Socket(socket).pure[F])(_ => release))
          case Left(error) => (toResponse(error), Resource.pure(Socket.empty[F]))
        }
  end DirectConnectApi
  private class UnsupportedConnectApi[F[_]: Applicative, Auth] extends ConnectApi[F, Auth, Unit]:
    def connect(state: Requested[F, Auth]): F[(Response, Unit)] = unsupportedCommand(state, ())
    def tunnel(state: Connected[F, Auth, Unit]): Resource[F, Socket[F]] = Resource.pure(Socket.empty[F])
  end UnsupportedConnectApi

  def direct[F[_]: {Async, Network}, Auth]: ResourceConnectApi[F, Auth] = new DirectConnectApi[F, Auth]
  def unsupported[F[_]: Applicative, Auth]: ConnectApi[F, Auth, Unit] = new UnsupportedConnectApi[F, Auth]
end ConnectApi
