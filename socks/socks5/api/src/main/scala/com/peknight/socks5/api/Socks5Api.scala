package com.peknight.socks5.api

import com.peknight.fs2.pull.state.BytePullState
import com.peknight.socks5.State
import com.peknight.socks5.State.{Negotiating, Requested, UsernamePasswordAuthenticating}
import com.peknight.socks5.api.Socks5Api.Socks5PullState
import com.peknight.socks5.auth.Method.{AuthRequiredMethod, NoAcceptableMethod}
import com.peknight.socks5.auth.password.Status.Failure

trait Socks5Api[F[_], Auth]:
  def negotiation(state: Negotiating): F[Either[NoAcceptableMethod.type | AuthRequiredMethod, Auth]]
  def passwordAuth(state: UsernamePasswordAuthenticating): F[Either[Failure, Auth]]
  def connect(state: Requested[Auth]): Socks5PullState[F, Auth, Unit]
end Socks5Api
object Socks5Api:
  type Socks5PullState[F[_], Auth, A] = BytePullState[F, Byte, State[Auth], A]
end Socks5Api
