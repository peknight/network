package com.peknight.socks5.server

import cats.syntax.applicative.*
import cats.syntax.applicativeError.*
import cats.{Applicative, ApplicativeError}
import com.peknight.socks5.Response
import com.peknight.socks5.state.State.Requested

package object api:
  def unsupportedCommand[F[_], Auth, S](state: Requested[F, Auth], s: S)(using Applicative[F]): F[(Response, S)] =
    (Response.unsupportedCommand, s).pure[F]
end api