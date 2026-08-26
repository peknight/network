package com.peknight.socks5.server.api

import com.peknight.socks5.server.state.ServerPullState

trait GSSApiApi[F[_], Auth]:
  def gssApi: ServerPullState.AUX[F, Auth]
end GSSApiApi
object GSSApiApi:
  private class UnsupportedGSSApiApi[F[_], Auth] extends GSSApiApi[F, Auth]:
    def gssApi: ServerPullState.AUX[F, Auth] = ServerPullState.unsupportedMethod[F, Auth]
  end UnsupportedGSSApiApi
  def unsupported[F[_], Auth]: GSSApiApi[F, Auth] = new UnsupportedGSSApiApi[F, Auth]
end GSSApiApi
