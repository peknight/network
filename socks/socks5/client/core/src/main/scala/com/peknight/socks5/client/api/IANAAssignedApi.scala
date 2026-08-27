package com.peknight.socks5.client.api

import com.peknight.socks5.client.state.ClientPullState

trait IANAAssignedApi[F[_], Auth]:
  def ianaAssigned: ClientPullState.Aux[F, Auth]
end IANAAssignedApi
object IANAAssignedApi:
  private class UnsupportedIANAAssignedApi[F[_], Auth] extends IANAAssignedApi[F, Auth]:
    def ianaAssigned: ClientPullState.Aux[F, Auth] = ClientPullState.unsupportedMethod[F, Auth]
  end UnsupportedIANAAssignedApi
  def unsupported[F[_], Auth]: IANAAssignedApi[F, Auth] = new UnsupportedIANAAssignedApi[F, Auth]
end IANAAssignedApi
