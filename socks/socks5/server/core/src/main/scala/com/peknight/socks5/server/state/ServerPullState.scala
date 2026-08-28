package com.peknight.socks5.server.state

import cats.effect.Async
import com.peknight.socks5.server.api.ServerApi
import com.peknight.socks5.state.{PullState, State}

import java.nio.charset.Charset

object ServerPullState:
  def apply[F[_], Auth, ConnectState, BindState, UDPAssociateState]
           (api: ServerApi[F, Auth, ConnectState, BindState, UDPAssociateState])
           (using Charset)(using Async[F]): PullState[F, State[F]] =
    val dsl = ServerPullStateDsl[F]
    dsl.state(api)
end ServerPullState
