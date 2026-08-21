package com.peknight.socks5.server.api

import cats.Applicative
import com.peknight.socks5.State.Requested
import com.peknight.socks5.server.state.unsupportedCommand
import com.peknight.socks5.{Response, Socks5PullState}

trait UDPAssociateApi[F[_], Auth, UDPAssociateState]:
  def udpAssociate(state: Requested[Auth]): F[(Response, UDPAssociateState)]
  def udpAssociated: Socks5PullState[F, Unit]
end UDPAssociateApi
object UDPAssociateApi:
  private class UnsupportedUDPAssociateApi[F[_]: Applicative, Auth] extends UDPAssociateApi[F, Auth, Unit]:
    def udpAssociate(state: Requested[Auth]): F[(Response, Unit)] = unsupportedCommand(state, ())
    def udpAssociated: Socks5PullState[F, Unit] = unsupportedCommand[F, Unit]
  end UnsupportedUDPAssociateApi
  def unsupported[F[_]: Applicative, Auth]: UDPAssociateApi[F, Auth, Unit] = new UnsupportedUDPAssociateApi[F, Auth]
end UDPAssociateApi
