package com.peknight.socks5.server.api

import cats.Applicative
import com.peknight.socks5.Response
import com.peknight.socks5.server.state.State.Requested
import com.peknight.socks5.server.state.{Socks5PullState, unsupportedCommand}

trait BindApi[F[_], Auth, BindState]:
  def bind(state: Requested[Auth]): F[(Response, BindState)]
  def bound: Socks5PullState[F, Unit]
end BindApi
object BindApi:
  private class UnsupportedBindApi[F[_]: Applicative, Auth] extends BindApi[F, Auth, Unit]:
    def bind(state: Requested[Auth]): F[(Response, Unit)] = unsupportedCommand(state, ())
    def bound: Socks5PullState[F, Unit] = unsupportedCommand[F, Unit]
  end UnsupportedBindApi
  def unsupported[F[_]: Applicative, Auth]: BindApi[F, Auth, Unit] = new UnsupportedBindApi[F, Auth]
end BindApi
