package com.peknight.socks5.server.api

import cats.Applicative
import com.peknight.socks5.Response
import com.peknight.socks5.server.state.ServerPullState
import com.peknight.socks5.state.State.Requested

trait UDPAssociateApi[F[_], Auth, UDPAssociateState]:
  def udpAssociate(state: Requested[Auth]): F[(Response, UDPAssociateState)]
  def udpAssociated: ServerPullState.AUX[F, Unit]
end UDPAssociateApi
object UDPAssociateApi:
  private class UnsupportedUDPAssociateApi[F[_]: Applicative, Auth] extends UDPAssociateApi[F, Auth, Unit]:
    def udpAssociate(state: Requested[Auth]): F[(Response, Unit)] = ServerPullState.unsupportedCommand(state, ())
    def udpAssociated: ServerPullState.AUX[F, Unit] = ServerPullState.unsupportedCommand[F, Unit]
  end UnsupportedUDPAssociateApi
  def unsupported[F[_]: Applicative, Auth]: UDPAssociateApi[F, Auth, Unit] = new UnsupportedUDPAssociateApi[F, Auth]
end UDPAssociateApi
