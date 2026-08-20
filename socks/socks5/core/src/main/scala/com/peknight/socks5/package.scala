package com.peknight

import com.peknight.fs2.pull.state.BytePullState
import com.peknight.socks5.State.Terminated

package object socks5:
  type Socks5PullState[F[_], A] = BytePullState[F, State, Terminated, A]
end socks5