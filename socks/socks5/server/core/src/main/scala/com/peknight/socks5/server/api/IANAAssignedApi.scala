package com.peknight.socks5.server.api

import com.peknight.socks5.api.unsupportedMethodS
import com.peknight.socks5.server.state.ServerPullStateDsl
import com.peknight.socks5.state.PullState

trait IANAAssignedApi[F[_], Auth]:
  def ianaAssigned: PullState[F, Auth]
end IANAAssignedApi
object IANAAssignedApi:
  private class UnsupportedIANAAssignedApi[F[_], Auth] extends IANAAssignedApi[F, Auth]:
    def ianaAssigned: PullState[F, Auth] = unsupportedMethodS[F, Auth]
  end UnsupportedIANAAssignedApi
  def unsupported[F[_], Auth]: IANAAssignedApi[F, Auth] = new UnsupportedIANAAssignedApi[F, Auth]
end IANAAssignedApi
