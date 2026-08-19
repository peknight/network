package com.peknight.socks5.api

import com.peknight.socks5.auth.Method
import com.peknight.socks5.auth.password.{Status, UsernamePassword}
import com.peknight.socks5.{Request, Response}

trait Socks5Api[F[_]]:
  def negotiation(methods: List[Method], ctx: ConnectionContext): F[Method]
  def passwordAuth(password: UsernamePassword, ctx: ConnectionContext): F[Status]
  def connect(req: Request, ctx: ConnectionContext): F[Response]
end Socks5Api
