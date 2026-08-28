package com.peknight.socks5.client.api

import com.peknight.socks5.api.unsupportedMethodS
import com.peknight.socks5.client.state.ClientPullStateDsl
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
