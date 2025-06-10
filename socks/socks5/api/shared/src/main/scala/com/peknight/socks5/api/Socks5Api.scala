package com.peknight.socks5.api

import com.peknight.socks5.auth.Method
import com.peknight.socks5.auth.password.{Status, UsernamePassword}
import com.peknight.socks5.{Request, Response}
import fs2.io.net.Socket

trait Socks5Api[F[_]]:
  def negotiation(methods: List[Method], socket: Socket[F]): F[Method]
  def passwordAuth(password: UsernamePassword, socket: Socket[F]): F[Status]
  def request(req: Request, socket: Socket[F]): F[Response]
end Socks5Api
