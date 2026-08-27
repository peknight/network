package com.peknight.socks5.client.api

import cats.effect.Resource
import cats.syntax.applicative.*
import cats.{Applicative, ApplicativeError}
import com.peknight.socks5.Response
import com.peknight.socks5.client.state.ClientPullState
import com.peknight.socks5.state.State.{Connected, Requested}
import fs2.{Pipe, Stream}

trait ConnectApi[F[_], Auth, ConnectState]:
  def connect(state: Requested[Auth], response: Response): F[ConnectState]
  def tunnel(state: Connected[Auth, ConnectState]): Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]
end ConnectApi
object ConnectApi:
  trait ResourceConnectApi[F[_], Auth]
    extends com.peknight.socks5.client.api.ConnectApi[F, Auth, Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]]:
    def tunnel(state: Connected[Auth, Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]])
    : Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])] =
      state.state
  end ResourceConnectApi
  private case class ConnectApi[F[_]: Applicative, Auth](stream: Stream[F, Byte], publish: Pipe[F, Byte, Unit])
    extends ResourceConnectApi[F, Auth]:
    def connect(state: Requested[Auth], response: Response): F[Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]] =
      Resource.pure[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]((publish, stream)).pure[F]
  end ConnectApi
  private class UnsupportedConnectApi[F[_], Auth](using ApplicativeError[F, Throwable])
    extends com.peknight.socks5.client.api.ConnectApi[F, Auth, Unit]:
    def connect(state: Requested[Auth], response: Response): F[Unit] =
      ClientPullState.unsupportedCommand(state, response)
    def tunnel(state: Connected[Auth, Unit]): Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])] =
      Resource.pure((_.drain, Stream.empty))
  end UnsupportedConnectApi

  def apply[F[_]: Applicative, Auth](stream: Stream[F, Byte], publish: Pipe[F, Byte, Unit])
  : com.peknight.socks5.client.api.ConnectApi[F, Auth, Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]] =
    ConnectApi(stream, publish)
  def unsupported[F[_], Auth](using ApplicativeError[F, Throwable])
  : com.peknight.socks5.client.api.ConnectApi[F, Auth, Unit] =
    new UnsupportedConnectApi[F, Auth]
end ConnectApi
