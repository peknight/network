package com.peknight.socks5.client

import cats.syntax.applicative.*
import cats.syntax.applicativeError.*
import cats.{Applicative, ApplicativeError}
import com.peknight.socks5.Response
import com.peknight.socks5.state.State.Requested

package object api:

  def unsupportedCommand[F[_], Auth, S](state: Requested[F, Auth], response: Response)
                                       (using ApplicativeError[F, Throwable]): F[S] =
    com.peknight.socks5.error.UnsupportedCommand(state.request.command.code).raiseError[F, S]
end api
