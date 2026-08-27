package com.peknight.socks5.server.api

import cats.Applicative
import com.peknight.socks5.Response
import com.peknight.socks5.server.state.ServerPullState
import com.peknight.socks5.state.State.Requested

trait BindApi[F[_], Auth, BindState]:
  def bind(state: Requested[Auth]): F[(Response, BindState)]
  def bound: ServerPullState.Aux[F, Unit]
end BindApi
object BindApi:
  private class UnsupportedBindApi[F[_]: Applicative, Auth] extends BindApi[F, Auth, Unit]:
    def bind(state: Requested[Auth]): F[(Response, Unit)] = ServerPullState.unsupportedCommand(state, ())
    def bound: ServerPullState.Aux[F, Unit] = ServerPullState.unsupportedCommand[F, Unit]
  end UnsupportedBindApi
  def unsupported[F[_]: Applicative, Auth]: BindApi[F, Auth, Unit] = new UnsupportedBindApi[F, Auth]
end BindApi
