package com.peknight.socks5.server.api

import com.peknight.socks5.Socks5PullState
import com.peknight.socks5.server.state.unsupportedMethod

trait GSSApiApi[F[_], Auth]:
  def gssApi: Socks5PullState[F, Auth]
end GSSApiApi
object GSSApiApi:
  private class UnsupportedGSSApiApi[F[_], Auth] extends GSSApiApi[F, Auth]:
    def gssApi: Socks5PullState[F, Auth] = unsupportedMethod[F, Auth]
  end UnsupportedGSSApiApi
  def unsupported[F[_], Auth]: GSSApiApi[F, Auth] = new UnsupportedGSSApiApi[F, Auth]
end GSSApiApi
