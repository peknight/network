package com.peknight.socks5.api

import com.peknight.socks5.Socks5PullState
import com.peknight.socks5.State.{Negotiating, Requested, UsernamePasswordAuthenticating}
import com.peknight.socks5.auth.Method.{AuthRequiredMethod, NoAcceptableMethod}
import com.peknight.socks5.auth.password.Status.Failure

trait Socks5Api[F[_], Auth]:
  def negotiation(state: Negotiating): F[Either[NoAcceptableMethod.type | AuthRequiredMethod, Auth]]
  def passwordAuth(state: UsernamePasswordAuthenticating): F[Either[Failure, Auth]]
  def connect(state: Requested[Auth]): Socks5PullState[F, Unit]
end Socks5Api
