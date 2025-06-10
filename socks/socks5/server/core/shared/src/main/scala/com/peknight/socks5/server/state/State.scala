package com.peknight.socks5.server.state

import cats.data.StateT
import com.peknight.cats.ext.instances.eitherT.eitherTMonad
import com.peknight.error.Error
import fs2.{Pull, Stream}

object State:
  def apply[F[_], O, A](f: Stream[F, Byte] => Pull[F, O, Either[Error, (Stream[F, Byte], A)]]): State[F, O, A] =
    StateT(f)
end State
