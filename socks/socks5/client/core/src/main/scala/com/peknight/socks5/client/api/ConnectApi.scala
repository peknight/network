package com.peknight.socks5.client.api

import cats.ApplicativeError
import cats.effect.{MonadCancel, Resource}
import com.peknight.socks5.Response
import com.peknight.socks5.client.state.ClientPullState
import com.peknight.socks5.state.State.{Connected, Requested}
import fs2.{Pipe, Stream}

trait ConnectApi[F[_], Auth, ConnectState]:
  def connect(state: Requested[Auth], response: Response): F[ConnectState]
  def tunnel(state: Connected[Auth, ConnectState]): Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]
end ConnectApi
object ConnectApi:
  private class UnsupportedConnectApi[F[_], Auth](using ApplicativeError[F, Throwable])
    extends ConnectApi[F, Auth, Unit]:
    def connect(state: Requested[Auth], response: Response): F[Unit] =
      ClientPullState.unsupportedCommand(state, response)
    def tunnel(state: Connected[Auth, Unit]): Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])] =
      Resource.pure((_.drain, Stream.empty))
  end UnsupportedConnectApi
  trait ResourceConnectApi[F[_], Auth](using MonadCancel[F, ?])
    extends ConnectApi[F, Auth, Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]]:
    def tunnel(state: Connected[Auth, Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]])
    : Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])] =
      state.state
  end ResourceConnectApi
  def unsupported[F[_], Auth](using ApplicativeError[F, Throwable]): ConnectApi[F, Auth, Unit] =
    new UnsupportedConnectApi[F, Auth]
end ConnectApi
