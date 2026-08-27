package com.peknight.socks5.client.api

import cats.ApplicativeError
import com.peknight.socks5.Response
import com.peknight.socks5.client.state.ClientPullState
import com.peknight.socks5.state.State.Requested

trait BindApi[F[_], Auth, BindState]:
  def bind(state: Requested[Auth], response: Response): F[BindState]
  def bound: ClientPullState.Aux[F, Unit]
end BindApi
object BindApi:
  private class UnsupportedBindApi[F[_], Auth](using ApplicativeError[F, Throwable]) extends BindApi[F, Auth, Unit]:
    def bind(state: Requested[Auth], response: Response): F[Unit] =
      ClientPullState.unsupportedCommand[F, Auth, Unit](state, response)
    def bound: ClientPullState.Aux[F, Unit] = ClientPullState.unsupportedCommand[F, Unit]
  end UnsupportedBindApi
  def unsupported[F[_], Auth](using ApplicativeError[F, Throwable]): BindApi[F, Auth, Unit] =
    new UnsupportedBindApi[F, Auth]
end BindApi
