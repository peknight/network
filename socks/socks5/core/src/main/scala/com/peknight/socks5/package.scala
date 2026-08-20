package com.peknight

import com.peknight.fs2.pull.state.BytePullState

package object socks5:
  type PullState[F[_], A] = BytePullState[F, Byte, State, A]
end socks5