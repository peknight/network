package com.peknight.socks5.api

import com.peknight.socks5.{Response, Socks5PullState}
import com.peknight.socks5.State.{Negotiating, Requested, UsernamePasswordAuthenticating}
import com.peknight.socks5.auth.Method.{AuthRequiredMethod, NoAcceptableMethod}
import com.peknight.socks5.auth.password.Status.Failure

trait Socks5Api[F[_], Auth, ConnectState, BindState, UDPAssociateState]:
  def negotiation(state: Negotiating): F[Either[NoAcceptableMethod.type | AuthRequiredMethod, Auth]]
  def usernamePassword(state: UsernamePasswordAuthenticating): F[Either[Failure, Auth]]
  def gssApi: Socks5PullState[F, Auth]
  def ianaAssigned: Socks5PullState[F, Auth]
  def privateMethod: Socks5PullState[F, Auth]
  def connect(state: Requested[Auth]): F[(Response, ConnectState)]
  def bind(state: Requested[Auth]): F[(Response, BindState)]
  def udpAssociate(state: Requested[Auth]): F[(Response, UDPAssociateState)]
  def connected: Socks5PullState[F, Unit]
  def bound: Socks5PullState[F, Unit]
  def udpAssociated: Socks5PullState[F, Unit]
end Socks5Api
