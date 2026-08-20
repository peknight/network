package com.peknight.socks5

import com.peknight.socks5.State.Terminated
import com.peknight.socks5.auth.Method
import com.peknight.socks5.auth.Method.*
import com.peknight.socks5.auth.password.Status.Failure
import com.peknight.socks5.auth.password.UsernamePassword as UPassword

import java.io.EOFException

sealed trait State:
  def connection: Connection
  def terminated: Boolean
  private[socks5] def error(error: Throwable): Terminated
  private[socks5] def eof: Terminated = error(new EOFException())
end State
object State:
  // 初始状态
  sealed trait InitialState extends State
  // 协商状态
  sealed trait NegotiationState extends InitialState:
    def methods: List[Method]
  end NegotiationState
  // 方法已选择状态
  sealed trait MethodSelectedState[+M <: Method] extends NegotiationState:
    def selected: M
  end MethodSelectedState
  // 可接受方法已选择状态
  sealed trait AcceptableMethodSelectedState[+M <: AcceptableMethod] extends MethodSelectedState[M]
  // 需认证方法状态
  sealed trait AuthRequiredMethodState[+M <: AuthRequiredMethod] extends AcceptableMethodSelectedState[M]
  // 用户名密码方法状态
  sealed trait UsernamePasswordState extends AuthRequiredMethodState[UsernamePassword.type]:
    def selected: UsernamePassword.type = UsernamePassword
  end UsernamePasswordState
  // 认证状态
  sealed trait AuthenticationState[+M <: AcceptableMethod] extends AcceptableMethodSelectedState[M]
  // 已认证状态
  sealed trait AuthenticatedState[+Auth] extends AuthenticationState[AcceptableMethod]:
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
  sealed trait Active extends State:
    def terminated: Boolean = false
  end Active

  // 结束状态
  sealed trait Terminated extends State:
    def terminated: Boolean = true
    private[socks5] def error(error: Throwable): Terminated = this
  end Terminated

  // 异常状态
  sealed trait ErrorState extends Terminated:
    def error: Throwable
  end ErrorState

  // 初始阶段
  sealed trait InitialPhase extends InitialState
  // 协商阶段
  sealed trait NegotiationPhase extends NegotiationState
  // 认证阶段
  sealed trait AuthenticationPhase[+M <: AcceptableMethod] extends AuthenticationState[M]
  // 请求阶段
  sealed trait RequestPhase[+Auth] extends RequestState[Auth]

  // 初始
  case class Initial private[socks5] (connection: Connection) extends InitialPhase with Active:
    private[socks5] def negotiating(methods: List[Method]): Negotiating = Negotiating(methods, connection)
    private[socks5] def error(error: Throwable): Terminated = InitialError(connection, error)
  end Initial

  // 初始异常
  case class InitialError private[socks5] (connection: Connection, error: Throwable)
    extends InitialPhase with ErrorState

  // 协商中
  case class Negotiating private[socks5] (methods: List[Method], connection: Connection)
    extends NegotiationPhase with Active:
    private[socks5] def noAuthenticationRequired[Auth](auth: Auth): Authenticated[Auth] =
      Authenticated(auth, NoAuthenticationRequired, methods, connection)
    private[socks5] def authRequiredMethod(selected: AuthRequiredMethod): AuthRequiredMethodSelected =
        AuthRequiredMethodSelected(selected, methods, connection)
    private[socks5] def noAcceptableMethod: NoAcceptableMethod = NoAcceptableMethod(methods, connection)
    private[socks5] def unsupportedMethod(selected: AcceptableMethod): UnsupportedMethod =
      UnsupportedMethod(selected, methods, connection)
    private[socks5] def error(error: Throwable): Terminated = NegotiationError(methods, connection, error)
  end Negotiating
  // 协商异常
  case class NegotiationError private[socks5] (methods: List[Method], connection: Connection, error: Throwable)
    extends NegotiationPhase with ErrorState
  // 没有可接受方法
  case class NoAcceptableMethod private[socks5] (methods: List[Method], connection: Connection)
    extends NegotiationPhase with MethodSelectedState[Method.NoAcceptableMethod.type] with Terminated:
    def selected: Method.NoAcceptableMethod.type = Method.NoAcceptableMethod
  end NoAcceptableMethod
  // 选择需认证方法
  case class AuthRequiredMethodSelected private[socks5](selected: AuthRequiredMethod, methods: List[Method],
                                                        connection: Connection)
    extends NegotiationPhase with AuthRequiredMethodState[AuthRequiredMethod] with Active:
    private[socks5] def passwordUnsafe(password: UPassword): UsernamePasswordAuthenticating =
      UsernamePasswordAuthenticating(password, methods, connection)
    private[socks5] def unsupportedMethod: UnsupportedMethod = UnsupportedMethod(selected, methods, connection)
    private[socks5] def error(error: Throwable): Terminated = AuthenticationError(selected, methods, connection, error)
  end AuthRequiredMethodSelected
  // 不支持的方法
  case class UnsupportedMethod private[socks5] (selected: AcceptableMethod, methods: List[Method],
                                                connection: Connection)
    extends NegotiationPhase with MethodSelectedState[AcceptableMethod] with Terminated

  // 认证通过
  case class Authenticated[Auth] private[socks5] (auth: Auth, selected: AcceptableMethod, methods: List[Method],
                                                  connection: Connection)
    extends AuthenticationPhase[AcceptableMethod] with AuthenticatedState[Auth] with Active:
    private[socks5] def requested(request: Request): Requested[Auth] = Requested(request, auth, selected, methods, connection)
    private[socks5] def error(error: Throwable): Terminated = RequestError(auth, selected, methods, connection, error)
  end Authenticated
  // 认证异常
  case class AuthenticationError(selected: AcceptableMethod, methods: List[Method], connection: Connection,
                                 error: Throwable)
    extends AuthenticationPhase[AcceptableMethod] with ErrorState
  // UsernamePassword认证中
  case class UsernamePasswordAuthenticating private[socks5] (password: UPassword, methods: List[Method],
                                                             connection: Connection)
    extends AuthenticationPhase[UsernamePassword.type] with UsernamePasswordState with Active:
    private[socks5] def authenticated[Auth](auth: Auth): Authenticated[Auth] =
      Authenticated(auth, selected, methods, connection)
    private[socks5] def failed(status: Failure): UsernamePasswordFailed =
      UsernamePasswordFailed(status, methods, connection)
    private[socks5] def error(error: Throwable): Terminated =
      UsernamePasswordAuthenticationError(methods, connection, error)
  end UsernamePasswordAuthenticating
  // UsernamePassword认证失败
  case class UsernamePasswordFailed private[socks5] (status: Failure, methods: List[Method], connection: Connection)
    extends AuthenticationPhase[UsernamePassword.type] with AuthRequiredMethodState[UsernamePassword.type]
      with UsernamePasswordState with Terminated
  // UsernamePassword认证异常
  case class UsernamePasswordAuthenticationError private[socks5] (methods: List[Method], connection: Connection,
                                                                  error: Throwable)
    extends AuthenticationPhase[UsernamePassword.type] with AuthRequiredMethodState[UsernamePassword.type]
      with UsernamePasswordState with ErrorState

  // 请求异常
  case class RequestError[Auth] private[socks5] (auth: Auth, selected: AcceptableMethod, methods: List[Method],
                                                 connection: Connection, error: Throwable)
    extends RequestPhase[Auth] with ErrorState
  // 已请求
  case class Requested[Auth] private[socks5] (request: Request, auth: Auth, selected: AcceptableMethod,
                                              methods: List[Method], connection: Connection)
    extends RequestPhase[Auth] with RequestedState[Auth] with Active:
    def error(error: Throwable): Terminated = RequestedError(request, auth, selected, methods, connection, error)
  end Requested
  // 已请求异常
  case class RequestedError[Auth] private[socks5] (request: Request, auth: Auth, selected: AcceptableMethod,
                                                   methods: List[Method], connection: Connection, error: Throwable)
    extends RequestPhase[Auth] with RequestedState[Auth] with ErrorState
end State
