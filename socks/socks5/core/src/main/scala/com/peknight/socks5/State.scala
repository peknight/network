package com.peknight.socks5

import com.peknight.socks5.auth.Method
import com.peknight.socks5.auth.Method.*
import com.peknight.socks5.auth.password.{Status, UsernamePassword as UPassword}

sealed trait State[+Auth]:
  def connection: Connection
  def terminated: Boolean
end State
object State:
  // 初始状态
  sealed trait InitialState[+Auth] extends State[Auth]
  // 协商状态
  sealed trait NegotiationState[+Auth] extends InitialState[Auth]:
    def methods: List[Method]
  end NegotiationState
  // 方法已选择状态
  sealed trait MethodSelectedState[+M <: Method, +Auth] extends NegotiationState[Auth]:
    def selected: M
  end MethodSelectedState
  // 可接受方法已选择状态
  sealed trait AcceptableMethodSelectedState[+M <: AcceptableMethod, +Auth] extends MethodSelectedState[M, Auth]
  // 需认证方法状态
  sealed trait AuthRequiredMethodState[+M <: AuthRequiredMethod] extends AcceptableMethodSelectedState[M, Nothing]
  // 用户名密码方法状态
  sealed trait UsernamePasswordState extends AuthRequiredMethodState[UsernamePassword.type]:
    def selected: UsernamePassword.type = UsernamePassword
  end UsernamePasswordState
  // 认证状态
  sealed trait AuthenticationState[+M <: AcceptableMethod, +Auth] extends AcceptableMethodSelectedState[M, Auth]
  // 已认证状态
  sealed trait AuthenticatedState[+Auth] extends AuthenticationState[AcceptableMethod, Auth]:
    def auth: Auth
  end AuthenticatedState
  // 请求状态
  sealed trait RequestState[+Auth] extends AuthenticatedState[Auth]
  // 已请求状态
  sealed trait RequestedState[+Auth] extends RequestState[Auth]:
    def request: Request
  end RequestedState
  // Connect状态
  sealed trait ConnectState[+Auth, S] extends RequestedState[Auth]
  // Bind状态
  sealed trait BindState[+Auth, S] extends RequestedState[Auth]
  // UDPAssociate状态
  sealed trait UDPAssociateState[+Auth, S] extends RequestedState[Auth]

  // 运行状态
  sealed trait Active[+Auth] extends State[Auth]:
    def terminated: Boolean = false
  end Active

  // 结束状态
  sealed trait Terminated[+Auth] extends State[Auth]:
    def terminated: Boolean = true
  end Terminated

  // 异常状态
  sealed trait ErrorState[+Auth] extends Terminated[Auth]:
    def error: Throwable
  end ErrorState

  // 初始阶段
  sealed trait InitialPhase extends InitialState[Nothing]
  // 协商阶段
  sealed trait NegotiationPhase extends NegotiationState[Nothing]
  // 认证阶段
  sealed trait AuthenticationPhase[+M <: AcceptableMethod, +Auth] extends AuthenticationState[M, Auth]
  // 认证失败
  sealed trait AuthFailed[+M <: AuthRequiredMethod] extends AuthenticationPhase[M, Nothing]
    with AuthRequiredMethodState[M] with Terminated[Nothing]
  // 请求阶段
  sealed trait RequestPhase[+Auth] extends RequestState[Auth]

  // 初始
  case class Initial private[socks5] (connection: Connection) extends InitialPhase with Active[Nothing]
  // 初始异常
  case class InitialError private[socks5] (connection: Connection, error: Throwable)
    extends InitialPhase with ErrorState[Nothing]

  // 协商中
  case class Negotiating private[socks5] (methods: List[Method], connection: Connection)
    extends NegotiationPhase with Active[Nothing]
  // 协商异常
  case class NegotiationError private[socks5] (methods: List[Method], connection: Connection, error: Throwable)
    extends NegotiationPhase with ErrorState[Nothing]
  // 没有可接受方法
  case class NoAcceptableMethod private[socks5] (methods: List[Method], connection: Connection)
    extends NegotiationPhase with MethodSelectedState[Method.NoAcceptableMethod.type, Nothing]
      with Terminated[Nothing]:
    def selected: Method.NoAcceptableMethod.type = Method.NoAcceptableMethod
  end NoAcceptableMethod
  // 选择需认证方法
  case class AuthRequiredMethodSelected private[socks5](selected: AuthRequiredMethod, methods: List[Method],
                                                        connection: Connection)
    extends NegotiationPhase with AuthRequiredMethodState[AuthRequiredMethod] with Active[Nothing]
  // 不支持的方法
  case class UnsupportedMethod private[socks5] (selected: AuthRequiredMethod, methods: List[Method],
                                                connection: Connection)
    extends NegotiationPhase with AuthRequiredMethodState[AuthRequiredMethod] with Terminated[Nothing]

  // 认证通过
  case class Authenticated[Auth] private[socks5] (auth: Auth, selected: AcceptableMethod, methods: List[Method],
                                                  connection: Connection)
    extends AuthenticationPhase[AcceptableMethod, Auth] with AuthenticatedState[Auth] with Active[Auth]
  // 认证异常
  case class AuthenticationError(selected: AcceptableMethod, methods: List[Method], connection: Connection,
                                 error: Throwable)
    extends AuthenticationPhase[AcceptableMethod, Nothing] with ErrorState[Nothing]
  // UsernamePassword认证中
  case class UsernamePasswordAuthenticating private[socks5] (password: UPassword, methods: List[Method],
                                                             connection: Connection)
    extends AuthFailed[UsernamePassword.type] with UsernamePasswordState
  // UsernamePassword认证失败
  case class UsernamePasswordFailed private[socks5] (status: Status, methods: List[Method], connection: Connection)
    extends AuthFailed[UsernamePassword.type] with UsernamePasswordState

  // 请求异常
  sealed trait RequestError[Auth] private[socks5] (auth: Auth, selected: AcceptableMethod, methods: List[Method],
                                                   connection: Connection, error: Throwable)
    extends RequestPhase[Auth] with ErrorState[Auth]
  // 已请求
  case class Requested[Auth] private[socks5] (request: Request, auth: Auth, selected: AcceptableMethod,
                                              methods: List[Method], connection: Connection)
    extends RequestPhase[Auth] with RequestedState[Auth] with Active[Auth]
end State
