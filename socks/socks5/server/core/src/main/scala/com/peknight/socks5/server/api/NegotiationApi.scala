package com.peknight.socks5.server.api

import cats.Applicative
import cats.syntax.applicative.*
import cats.syntax.either.*
import com.peknight.socks5.auth.Method.{AuthRequiredMethod, NoAcceptableMethod, NoAuthenticationRequired}
import com.peknight.socks5.state.State.Negotiating

trait NegotiationApi[F[_], Auth]:
  def negotiation(state: Negotiating[F]): F[Either[NoAcceptableMethod.type | AuthRequiredMethod, Auth]]
end NegotiationApi
object NegotiationApi:
  private class NoAuthenticationRequiredNegotiationApi[F[_]: Applicative] extends NegotiationApi[F, Unit]:
    def negotiation(state: Negotiating[F]): F[Either[NoAcceptableMethod.type | AuthRequiredMethod, Unit]] =
      if state.methods.contains(NoAuthenticationRequired) then
        ().asRight[NoAcceptableMethod.type | AuthRequiredMethod].pure[F]
      else NoAcceptableMethod.asLeft[Unit].pure[F]
  end NoAuthenticationRequiredNegotiationApi
  def noAuthenticationRequired[F[_]: Applicative]: NegotiationApi[F, Unit] =
    new NoAuthenticationRequiredNegotiationApi[F]
end NegotiationApi
