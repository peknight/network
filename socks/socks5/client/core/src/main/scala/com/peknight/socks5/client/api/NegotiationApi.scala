package com.peknight.socks5.client.api

import cats.Applicative
import cats.syntax.applicative.*
import com.peknight.socks5.auth.Method
import com.peknight.socks5.auth.Method.NoAuthenticationRequired
import com.peknight.socks5.state.State.{Initial, Negotiating}

trait NegotiationApi[F[_], Auth]:
  def negotiation(state: Initial[F]): F[List[Method]]
  def noAuthenticationRequired(state: Negotiating[F]): F[Auth]
end NegotiationApi
object NegotiationApi:
  private case class NegotiationApi[F[_]: Applicative, Auth](methods: List[Method], noAuthenticationRequiredAuth: Auth)
    extends com.peknight.socks5.client.api.NegotiationApi[F, Auth]:
    def negotiation(state: Initial[F]): F[List[Method]] = List(NoAuthenticationRequired).pure[F]
    def noAuthenticationRequired(state: Negotiating[F]): F[Auth] = noAuthenticationRequiredAuth.pure[F]
  end NegotiationApi
  private class NoAuthenticationRequiredNegotiationApi[F[_]: Applicative]
    extends com.peknight.socks5.client.api.NegotiationApi[F, Unit]:
    def negotiation(state: Initial[F]): F[List[Method]] = List(NoAuthenticationRequired).pure[F]
    def noAuthenticationRequired(state: Negotiating[F]): F[Unit] = ().pure[F]
  end NoAuthenticationRequiredNegotiationApi

  def apply[F[_]: Applicative, Auth](methods: List[Method], noAuthenticationRequiredAuth: Auth)
  : com.peknight.socks5.client.api.NegotiationApi[F, Auth] =
    NegotiationApi(methods, noAuthenticationRequiredAuth)
  def noAuthenticationRequired[F[_]: Applicative]: com.peknight.socks5.client.api.NegotiationApi[F, Unit] =
    new NoAuthenticationRequiredNegotiationApi[F]
end NegotiationApi
