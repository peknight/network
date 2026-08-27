package com.peknight.socks5.client.api

import cats.ApplicativeError
import cats.syntax.applicativeError.*
import com.peknight.auth.UserPassword
import com.peknight.socks5.auth.Method.UsernamePassword
import com.peknight.socks5.error.UnsupportedMethod
import com.peknight.socks5.state.State.{AuthRequiredMethodSelected, UsernamePasswordAuthenticating}

trait UsernamePasswordApi[F[_], Auth]:
  def usernamePassword(state: AuthRequiredMethodSelected): F[UserPassword]
  def authenticated(state: UsernamePasswordAuthenticating): F[Auth]
end UsernamePasswordApi
object UsernamePasswordApi:
  private class UnsupportedUsernamePasswordApi[F[_], Auth](using ApplicativeError[F, Throwable])
    extends UsernamePasswordApi[F, Auth]:
    def usernamePassword(state: AuthRequiredMethodSelected): F[UserPassword] =
      UnsupportedMethod(UsernamePassword).raiseError[F, UserPassword]
    def authenticated(state: UsernamePasswordAuthenticating): F[Auth] =
      UnsupportedMethod(UsernamePassword).raiseError[F, Auth]
  end UnsupportedUsernamePasswordApi
  def unsupported[F[_], Auth](using ApplicativeError[F, Throwable]): UsernamePasswordApi[F, Auth] =
    new UnsupportedUsernamePasswordApi[F, Auth]
end UsernamePasswordApi
