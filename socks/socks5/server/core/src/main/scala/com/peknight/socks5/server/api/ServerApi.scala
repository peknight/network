package com.peknight.socks5.server.api

trait ServerApi[F[_], Auth, ConnectState, BindState, UDPAssociateState]:
  def negotiationApi: NegotiationApi[F, Auth]
  def usernamePasswordApi: UsernamePasswordApi[F, Auth]
  def gssApiApi: GSSApiApi[F, Auth]
  def ianaAssignedApi: IANAAssignedApi[F, Auth]
  def privateMethodApi: PrivateMethodApi[F, Auth]
  def connectApi: ConnectApi[F, Auth, ConnectState]
  def bindApi: BindApi[F, Auth, BindState]
  def udpAssociateApi: UDPAssociateApi[F, Auth, UDPAssociateState]
end ServerApi
object ServerApi:
  private case class ServerApi[F[_], Auth, ConnectState, BindState, UDPAssociateState](
    negotiationApi: NegotiationApi[F, Auth],
    usernamePasswordApi: UsernamePasswordApi[F, Auth],
    gssApiApi: GSSApiApi[F, Auth],
    ianaAssignedApi: IANAAssignedApi[F, Auth],
    privateMethodApi: PrivateMethodApi[F, Auth],
    connectApi: ConnectApi[F, Auth, ConnectState],
    bindApi: BindApi[F, Auth, BindState],
    udpAssociateApi: UDPAssociateApi[F, Auth, UDPAssociateState]
  ) extends com.peknight.socks5.server.api.ServerApi[F, Auth, ConnectState, BindState, UDPAssociateState]

  def apply[F[_], Auth, ConnectState, BindState, UDPAssociateState](
    negotiationApi: NegotiationApi[F, Auth],
    usernamePasswordApi: UsernamePasswordApi[F, Auth],
    gssApiApi: GSSApiApi[F, Auth],
    ianaAssignedApi: IANAAssignedApi[F, Auth],
    privateMethodApi: PrivateMethodApi[F, Auth],
    connectApi: ConnectApi[F, Auth, ConnectState],
    bindApi: BindApi[F, Auth, BindState],
    udpAssociateApi: UDPAssociateApi[F, Auth, UDPAssociateState]
  ): com.peknight.socks5.server.api.ServerApi[F, Auth, ConnectState, BindState, UDPAssociateState] =
    ServerApi(negotiationApi, usernamePasswordApi, gssApiApi, ianaAssignedApi, privateMethodApi, connectApi, bindApi,
      udpAssociateApi)
end ServerApi
