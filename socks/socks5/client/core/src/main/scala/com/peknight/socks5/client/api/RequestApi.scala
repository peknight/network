package com.peknight.socks5.client.api

import cats.Applicative
import cats.syntax.applicative.*
import com.peknight.ip4s.HostPort
import com.peknight.socks5.Command.CONNECT
import com.peknight.socks5.Request
import com.peknight.socks5.state.State.Authenticated

trait RequestApi[F[_], Auth]:
  def request(state: Authenticated[F, Auth]): F[Request]
end RequestApi
object RequestApi:
  private case class RequestApi[F[_]: Applicative, Auth](req: Request)
    extends com.peknight.socks5.client.api.RequestApi[F, Auth]:
    def request(state: Authenticated[F, Auth]): F[Request] = req.pure[F]
  end RequestApi
  def apply[F[_]: Applicative, Auth](req: Request): com.peknight.socks5.client.api.RequestApi[F, Auth] =
    RequestApi[F, Auth](req)
  def connect[F[_]: Applicative, Auth](address: HostPort): com.peknight.socks5.client.api.RequestApi[F, Auth] =
    RequestApi[F, Auth](Request(CONNECT, address.host, address.port))
end RequestApi
