package com.peknight.socks5.server.api

import cats.Applicative
import cats.effect.{MonadCancel, Resource}
import com.peknight.socks5.Response
import com.peknight.socks5.state.State.{Connected, Requested}
import fs2.{Pipe, Stream}

trait ConnectApi[F[_], Auth, ConnectState]:
  def connect(state: Requested[F, Auth]): F[(Response, ConnectState)]
  def tunnel(state: Connected[F, Auth, ConnectState]): Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]
end ConnectApi
object ConnectApi:
  trait ResourceConnectApi[F[_], Auth](using MonadCancel[F, ?])
    extends ConnectApi[F, Auth, Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]]:
    def tunnel(state: Connected[F, Auth, Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]])
    : Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])] =
      state.state
  end ResourceConnectApi
  private class UnsupportedConnectApi[F[_]: Applicative, Auth] extends ConnectApi[F, Auth, Unit]:
    def connect(state: Requested[F, Auth]): F[(Response, Unit)] = unsupportedCommand(state, ())
    def tunnel(state: Connected[F, Auth, Unit]): Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])] =
      Resource.pure((_.drain, Stream.empty))
  end UnsupportedConnectApi
  def unsupported[F[_]: Applicative, Auth]: ConnectApi[F, Auth, Unit] = new UnsupportedConnectApi[F, Auth]
end ConnectApi
