package com.peknight.socks5.client.api

import cats.Applicative
import cats.syntax.applicative.*
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
end RequestApi
