package com.peknight.socks5.client.api

import com.peknight.socks5.client.state.ClientPullState

trait PrivateMethodApi[F[_], Auth]:
  def privateMethod: ClientPullState.Aux[F, Auth]
end PrivateMethodApi
object PrivateMethodApi:
  private class UnsupportedPrivateMethodApi[F[_], Auth] extends PrivateMethodApi[F, Auth]:
    def privateMethod: ClientPullState.Aux[F, Auth] = ClientPullState.unsupportedMethod[F, Auth]
  end UnsupportedPrivateMethodApi
  def unsupported[F[_], Auth]: PrivateMethodApi[F, Auth] = new UnsupportedPrivateMethodApi[F, Auth]
end PrivateMethodApi
