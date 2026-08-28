package com.peknight.socks5.client.state

import cats.effect.Async
import com.peknight.socks5.client.api.ClientApi
import com.peknight.socks5.state.{PullState, State}

import java.nio.charset.Charset

object ClientPullState:
  def apply[F[_], Auth, ConnectState, BindState, UDPAssociateState]
           (api: ClientApi[F, Auth, ConnectState, BindState, UDPAssociateState])
           (using Charset)(using Async[F]): PullState[F, State[F]] =
    val dsl = ClientPullStateDsl[F]
    dsl.state(api)
end ClientPullState
