package com.peknight.socks5.client.api

import com.peknight.socks5.api.unsupportedMethodS
import com.peknight.socks5.client.state.ClientPullStateDsl
import com.peknight.socks5.state.PullState

trait GSSApiApi[F[_], Auth]:
  def gssApi: PullState[F, Auth]
end GSSApiApi
object GSSApiApi:
  private class UnsupportedGSSApiApi[F[_], Auth] extends GSSApiApi[F, Auth]:
    def gssApi: PullState[F, Auth] = unsupportedMethodS[F, Auth]
  end UnsupportedGSSApiApi
  def unsupported[F[_], Auth]: GSSApiApi[F, Auth] = new UnsupportedGSSApiApi[F, Auth]
end GSSApiApi
