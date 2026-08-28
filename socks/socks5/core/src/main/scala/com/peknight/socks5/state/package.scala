package com.peknight.socks5

import cats.data.StateT
import com.peknight.socks5.state.State.Terminated
import fs2.{Pull, Stream}

package object state:
  type PullState[F[_], A] = StateT[[X] =>> Pull[F, Byte, Either[Terminated[F], X]], (State[F], Stream[F, Byte]), A]
end state