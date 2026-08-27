package com.peknight.socks5.client.api

import cats.ApplicativeError
import com.peknight.socks5.Response
import com.peknight.socks5.client.state.ClientPullState
import com.peknight.socks5.state.State.{Connected, Requested}
import fs2.Pipe

trait ConnectApi[F[_], Auth, ConnectState]:
  def connect(state: Requested[Auth], response: Response): F[ConnectState]
  def pipe(state: Connected[Auth, ConnectState]): Pipe[F, Byte, Byte]
end ConnectApi
object ConnectApi:
  private class UnsupportedConnectApi[F[_], Auth](using ApplicativeError[F, Throwable])
    extends ConnectApi[F, Auth, Unit]:
    def connect(state: Requested[Auth], response: Response): F[Unit] =
      ClientPullState.unsupportedCommand(state, response)
    def pipe(state: Connected[Auth, Unit]): Pipe[F, Byte, Byte] = _.drain
  end UnsupportedConnectApi
  def unsupported[F[_], Auth](using ApplicativeError[F, Throwable]): ConnectApi[F, Auth, Unit] =
    new UnsupportedConnectApi[F, Auth]
end ConnectApi
