package com.peknight.socks5.client.api

trait ClientApi[F[_], Auth, ConnectState, BindState, UDPAssociateState]:
  def negotiationApi: NegotiationApi[F, Auth]
  def usernamePasswordApi: UsernamePasswordApi[F, Auth]
  def gssApiApi: GSSApiApi[F, Auth]
  def ianaAssignedApi: IANAAssignedApi[F, Auth]
  def privateMethodApi: PrivateMethodApi[F, Auth]
  def requestApi: RequestApi[F, Auth]
  def connectApi: ConnectApi[F, Auth, ConnectState]
  def bindApi: BindApi[F, Auth, BindState]
  def udpAssociateApi: UDPAssociateApi[F, Auth, UDPAssociateState]
end ClientApi
object ClientApi:
  private case class ClientApi[F[_], Auth, ConnectState, BindState, UDPAssociateState](
    negotiationApi: NegotiationApi[F, Auth],
    usernamePasswordApi: UsernamePasswordApi[F, Auth],
    gssApiApi: GSSApiApi[F, Auth],
    ianaAssignedApi: IANAAssignedApi[F, Auth],
    privateMethodApi: PrivateMethodApi[F, Auth],
    requestApi: RequestApi[F, Auth],
    connectApi: ConnectApi[F, Auth, ConnectState],
    bindApi: BindApi[F, Auth, BindState],
    udpAssociateApi: UDPAssociateApi[F, Auth, UDPAssociateState]
  ) extends com.peknight.socks5.client.api.ClientApi[F, Auth, ConnectState, BindState, UDPAssociateState]

  def apply[F[_], Auth, ConnectState, BindState, UDPAssociateState](
    negotiationApi: NegotiationApi[F, Auth],
    usernamePasswordApi: UsernamePasswordApi[F, Auth],
    gssApiApi: GSSApiApi[F, Auth],
    ianaAssignedApi: IANAAssignedApi[F, Auth],
    privateMethodApi: PrivateMethodApi[F, Auth],
    requestApi: RequestApi[F, Auth],
    connectApi: ConnectApi[F, Auth, ConnectState],
    bindApi: BindApi[F, Auth, BindState],
    udpAssociateApi: UDPAssociateApi[F, Auth, UDPAssociateState]
  ): com.peknight.socks5.client.api.ClientApi[F, Auth, ConnectState, BindState, UDPAssociateState] =
    ClientApi(negotiationApi, usernamePasswordApi, gssApiApi, ianaAssignedApi, privateMethodApi, requestApi, connectApi,
      bindApi, udpAssociateApi)
end ClientApi
