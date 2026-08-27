package com.peknight.socks5.client.api

import cats.Applicative
import cats.syntax.applicative.*
import com.peknight.socks5.auth.Method
import com.peknight.socks5.auth.Method.NoAuthenticationRequired
import com.peknight.socks5.state.State.{Initial, Negotiating}

trait NegotiationApi[F[_], Auth]:
  def negotiation(state: Initial): F[List[Method]]
  def noAuthenticationRequired(state: Negotiating): F[Auth]
end NegotiationApi
object NegotiationApi:
  private class NoAuthenticationRequiredNegotiationApi[F[_]: Applicative] extends NegotiationApi[F, Unit]:
    def negotiation(state: Initial): F[List[Method]] = List(NoAuthenticationRequired).pure[F]
    def noAuthenticationRequired(state: Negotiating): F[Unit] = ().pure[F]
  end NoAuthenticationRequiredNegotiationApi
  def noAuthenticationRequired[F[_]: Applicative]: NegotiationApi[F, Unit] =
    new NoAuthenticationRequiredNegotiationApi[F]
end NegotiationApi
