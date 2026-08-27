package com.peknight.socks5.client.api

import cats.syntax.applicative.*
import cats.syntax.applicativeError.*
import cats.{Applicative, ApplicativeError}
import com.peknight.auth.UserPassword
import com.peknight.socks5.auth.Method.UsernamePassword
import com.peknight.socks5.error.UnsupportedMethod
import com.peknight.socks5.state.State.{AuthRequiredMethodSelected, UsernamePasswordAuthenticating}

trait UsernamePasswordApi[F[_], Auth]:
  def usernamePassword(state: AuthRequiredMethodSelected): F[UserPassword]
  def authenticated(state: UsernamePasswordAuthenticating): F[Auth]
end UsernamePasswordApi
object UsernamePasswordApi:
  private case class UsernamePasswordApi[F[_]: Applicative, Auth](userPassword: UserPassword, auth: Auth)
    extends com.peknight.socks5.client.api.UsernamePasswordApi[F, Auth]:
    def usernamePassword(state: AuthRequiredMethodSelected): F[UserPassword] = userPassword.pure[F]
    def authenticated(state: UsernamePasswordAuthenticating): F[Auth] = auth.pure[F]
  end UsernamePasswordApi
  private class UnsupportedUsernamePasswordApi[F[_], Auth](using ApplicativeError[F, Throwable])
    extends com.peknight.socks5.client.api.UsernamePasswordApi[F, Auth]:
    def usernamePassword(state: AuthRequiredMethodSelected): F[UserPassword] =
      UnsupportedMethod(UsernamePassword).raiseError[F, UserPassword]
    def authenticated(state: UsernamePasswordAuthenticating): F[Auth] =
      UnsupportedMethod(UsernamePassword).raiseError[F, Auth]
  end UnsupportedUsernamePasswordApi

  def apply[F[_]: Applicative, Auth](userPassword: UserPassword, auth: Auth)
  : com.peknight.socks5.client.api.UsernamePasswordApi[F, Auth] =
    UsernamePasswordApi(userPassword, auth)
  def unsupported[F[_], Auth](using ApplicativeError[F, Throwable])
  : com.peknight.socks5.client.api.UsernamePasswordApi[F, Auth] =
    new UnsupportedUsernamePasswordApi[F, Auth]
end UsernamePasswordApi
