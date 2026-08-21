package com.peknight.socks5.server.api

import cats.Applicative
import cats.syntax.applicative.*
import cats.syntax.either.*
import com.peknight.socks5.State.UsernamePasswordAuthenticating
import com.peknight.socks5.auth.password.Status.Failure

trait UsernamePasswordApi[F[_], Auth]:
  def usernamePassword(state: UsernamePasswordAuthenticating): F[Either[Failure, Auth]]
end UsernamePasswordApi
object UsernamePasswordApi:
  private class UnsupportedUsernamePasswordApi[F[_]: Applicative, Auth] extends UsernamePasswordApi[F, Auth]:
    def usernamePassword(state: UsernamePasswordAuthenticating): F[Either[Failure, Auth]] =
      Failure.default.asLeft[Auth].pure[F]
  end UnsupportedUsernamePasswordApi
  def unsupported[F[_]: Applicative, Auth]: UsernamePasswordApi[F, Auth] = new UnsupportedUsernamePasswordApi[F, Auth]
end UsernamePasswordApi
