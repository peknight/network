package com.peknight.socks5.client.api

import com.peknight.socks5.api.unsupportedMethodS
import com.peknight.socks5.client.state.ClientPullStateDsl
import com.peknight.socks5.state.PullState

trait PrivateMethodApi[F[_], Auth]:
  def privateMethod: PullState[F, Auth]
end PrivateMethodApi
object PrivateMethodApi:
  private class UnsupportedPrivateMethodApi[F[_], Auth] extends PrivateMethodApi[F, Auth]:
    def privateMethod: PullState[F, Auth] = unsupportedMethodS[F, Auth]
  end UnsupportedPrivateMethodApi
  def unsupported[F[_], Auth]: PrivateMethodApi[F, Auth] = new UnsupportedPrivateMethodApi[F, Auth]
end PrivateMethodApi
