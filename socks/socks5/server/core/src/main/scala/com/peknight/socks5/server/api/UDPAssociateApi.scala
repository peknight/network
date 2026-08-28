package com.peknight.socks5.server.api

import cats.Applicative
import com.peknight.socks5.Response
import com.peknight.socks5.api.unsupportedCommandS
import com.peknight.socks5.state.PullState
import com.peknight.socks5.state.State.Requested

trait UDPAssociateApi[F[_], Auth, UDPAssociateState]:
  def udpAssociate(state: Requested[F, Auth]): F[(Response, UDPAssociateState)]
  def udpAssociated: PullState[F, Unit]
end UDPAssociateApi
object UDPAssociateApi:
  private class UnsupportedUDPAssociateApi[F[_]: Applicative, Auth] extends UDPAssociateApi[F, Auth, Unit]:
    def udpAssociate(state: Requested[F, Auth]): F[(Response, Unit)] = unsupportedCommand(state, ())
    def udpAssociated: PullState[F, Unit] = unsupportedCommandS[F, Unit]
  end UnsupportedUDPAssociateApi
  def unsupported[F[_]: Applicative, Auth]: UDPAssociateApi[F, Auth, Unit] = new UnsupportedUDPAssociateApi[F, Auth]
end UDPAssociateApi
