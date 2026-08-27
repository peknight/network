package com.peknight.socks5.client.api

import com.peknight.socks5.client.state.ClientPullState

trait GSSApiApi[F[_], Auth]:
  def gssApi: ClientPullState.Aux[F, Auth]
end GSSApiApi
object GSSApiApi:
  private class UnsupportedGSSApiApi[F[_], Auth] extends GSSApiApi[F, Auth]:
    def gssApi: ClientPullState.Aux[F, Auth] = ClientPullState.unsupportedMethod[F, Auth]
  end UnsupportedGSSApiApi
  def unsupported[F[_], Auth]: GSSApiApi[F, Auth] = new UnsupportedGSSApiApi[F, Auth]
end GSSApiApi
