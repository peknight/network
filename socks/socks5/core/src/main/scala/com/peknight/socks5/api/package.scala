package com.peknight.socks5

import com.peknight.cats.instances.eitherT.given
import com.peknight.socks5.state.State.{AuthRequiredMethodSelected, Requested}
import com.peknight.socks5.state.{PullState, PullStateDsl}

package object api:
  def unsupportedMethodS[F[_], A]: PullState[F, A] =
    val dsl = PullStateDsl[F]
    import dsl.*
    for
      state <- typedS[AuthRequiredMethodSelected[F]]
      a <- liftL[A](state.unsupportedMethod)
    yield
      a

  def unsupportedCommandS[F[_], A]: PullState[F, A] =
    val dsl = PullStateDsl[F]
    import dsl.*
    for
      state <- typedS[Requested[F, ?]]
      a <- liftL[A](state.unsupportedCommand)
    yield
      a
end api