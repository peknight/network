package com.peknight.socks5.server.api

import com.peknight.socks5.server.state.{Socks5PullState, unsupportedMethod}

trait PrivateMethodApi[F[_], Auth]:
  def privateMethod: Socks5PullState[F, Auth]
end PrivateMethodApi
object PrivateMethodApi:
  private class UnsupportedPrivateMethodApi[F[_], Auth] extends PrivateMethodApi[F, Auth]:
    def privateMethod: Socks5PullState[F, Auth] = unsupportedMethod[F, Auth]
  end UnsupportedPrivateMethodApi
  def unsupported[F[_], Auth]: PrivateMethodApi[F, Auth] = new UnsupportedPrivateMethodApi[F, Auth]
end PrivateMethodApi
