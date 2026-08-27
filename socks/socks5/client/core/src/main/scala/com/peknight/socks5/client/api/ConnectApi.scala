package com.peknight.socks5.client.api

import cats.ApplicativeError
import com.peknight.socks5.Response
import com.peknight.socks5.client.state.ClientPullState
import com.peknight.socks5.state.State.Requested

trait ConnectApi[F[_], Auth, ConnectState]:
  def connect(state: Requested[Auth], response: Response): F[ConnectState]
end ConnectApi
object ConnectApi:
  private class UnsupportedConnectApi[F[_], Auth](using ApplicativeError[F, Throwable])
    extends ConnectApi[F, Auth, Unit]:
    def connect(state: Requested[Auth], response: Response): F[Unit] =
      ClientPullState.unsupportedCommand(state, response)
  end UnsupportedConnectApi
  def unsupported[F[_], Auth](using ApplicativeError[F, Throwable]): ConnectApi[F, Auth, Unit] =
    new UnsupportedConnectApi[F, Auth]
end ConnectApi
