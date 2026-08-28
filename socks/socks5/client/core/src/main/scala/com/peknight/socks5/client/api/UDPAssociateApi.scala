package com.peknight.socks5.client.api

import cats.ApplicativeError
import com.peknight.socks5.Response
import com.peknight.socks5.api.unsupportedCommandS
import com.peknight.socks5.client.state.ClientPullStateDsl
import com.peknight.socks5.state.PullState
import com.peknight.socks5.state.State.Requested

trait UDPAssociateApi[F[_], Auth, UDPAssociateState]:
  def udpAssociate(state: Requested[F, Auth], response: Response): F[UDPAssociateState]
  def udpAssociated: PullState[F, Unit]
end UDPAssociateApi
object UDPAssociateApi:
  private class UnsupportedUDPAssociateApi[F[_], Auth](using ApplicativeError[F, Throwable])
    extends UDPAssociateApi[F, Auth, Unit]:
    def udpAssociate(state: Requested[F, Auth], response: Response): F[Unit] =
      unsupportedCommand[F, Auth, Unit](state, response)
    def udpAssociated: PullState[F, Unit] = unsupportedCommandS[F, Unit]
  end UnsupportedUDPAssociateApi
  def unsupported[F[_], Auth](using ApplicativeError[F, Throwable]): UDPAssociateApi[F, Auth, Unit] =
    new UnsupportedUDPAssociateApi[F, Auth]
end UDPAssociateApi
