package com.peknight.socks5.server.api

import com.peknight.socks5.server.state.{Socks5PullState, unsupportedMethod}

trait IANAAssignedApi[F[_], Auth]:
  def ianaAssigned: Socks5PullState[F, Auth]
end IANAAssignedApi
object IANAAssignedApi:
  private class UnsupportedIANAAssignedApi[F[_], Auth] extends IANAAssignedApi[F, Auth]:
    def ianaAssigned: Socks5PullState[F, Auth] = unsupportedMethod[F, Auth]
  end UnsupportedIANAAssignedApi
  def unsupported[F[_], Auth]: IANAAssignedApi[F, Auth] = new UnsupportedIANAAssignedApi[F, Auth]
end IANAAssignedApi
