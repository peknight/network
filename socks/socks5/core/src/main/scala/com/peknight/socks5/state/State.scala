package com.peknight.socks5.state

import com.peknight.auth.UserPassword
import com.peknight.socks.Connection
import com.peknight.socks5.auth.Method
import com.peknight.socks5.auth.Method.*
import com.peknight.socks5.auth.password.Status.Failure
import com.peknight.socks5.state.State.Terminated
import com.peknight.socks5.{Request, Response}
import scodec.bits.ByteVector

sealed trait State:
  def connection: Connection
  def terminated: Boolean
  private[socks5] def error(error: Throwable): Terminated
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
  // 已回复状态
  sealed trait RespondedState[+Auth, +S] extends RequestedState[Auth]:
    def response: Response
    def addressBytes: ByteVector
    def state: S
  end RespondedState
  sealed trait RespondedSuccessState[+Auth, +S] extends RespondedState[Auth, S]
  sealed trait RespondedFailedState[+Auth, +S] extends RespondedState[Auth, S] with Terminated
  // Connect状态
  sealed trait ConnectedState[+Auth, +S] extends RespondedState[Auth, S]
  // Bind状态
  sealed trait BoundState[+Auth, +S] extends RespondedState[Auth, S]
  // UDPAssociate状态
  sealed trait UDPAssociatedState[+Auth, +S] extends RespondedState[Auth, S]

  // 正常关闭
  sealed trait Closed[+Auth, +S] extends RespondedSuccessState[Auth, S] with Terminated

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
  sealed trait RespondedPhase[+Auth, +S] extends RespondedState[Auth, S]
  // Connect阶段
  sealed trait ConnectedPhase[+Auth, +S] extends RespondedPhase[Auth, S] with ConnectedState[Auth, S]
  // Bind阶段
  sealed trait BoundPhase[+Auth, +S] extends RespondedPhase[Auth, S] with BoundState[Auth, S]
  // UDPAssociate阶段
  sealed trait UDPAssociatedPhase[+Auth, +S] extends RespondedPhase[Auth, S] with UDPAssociatedState[Auth, S]

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
    private[socks5] def passwordUnsafe(userPassword: UserPassword): UsernamePasswordAuthenticating =
      UsernamePasswordAuthenticating(userPassword, methods, connection)
    private[socks5] def unsupportedMethod: UnsupportedMethod = UnsupportedMethod(selected, methods, connection)
    private[socks5] def error(error: Throwable): Terminated = error match
      case com.peknight.socks5.error.UnsupportedMethod(_) => UnsupportedMethod(selected, methods, connection)
      case _ => AuthenticationError(selected, methods, connection, error)
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
  case class UsernamePasswordAuthenticating private[socks5] (userPassword: UserPassword, methods: List[Method],
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
    private[socks5] def connected[S](response: Response, state: S, addressBytes: ByteVector): Connected[Auth, S] =
      Connected(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def bound[S](response: Response, state: S, addressBytes: ByteVector): Bound[Auth, S] =
      Bound(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def udpAssociated[S](response: Response, state: S, addressBytes: ByteVector): UDPAssociated[Auth, S] =
      UDPAssociated(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def connectFailed[S](response: Response, state: S, addressBytes: ByteVector): ConnectFailed[Auth, S] =
      ConnectFailed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def bindFailed[S](response: Response, state: S, addressBytes: ByteVector): BindFailed[Auth, S] =
      BindFailed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def udpAssociateFailed[S](response: Response, state: S, addressBytes: ByteVector): UDPAssociateFailed[Auth, S] =
      UDPAssociateFailed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def unsupportedCommand: UnsupportedCommand[Auth] =
      UnsupportedCommand(request, auth, selected, methods, connection)
    private[socks5] def error(error: Throwable): Terminated = RequestedError(request, auth, selected, methods, connection, error)
  end Requested
  case class UnsupportedCommand[Auth] private[socks5] (request: Request, auth: Auth, selected: AcceptableMethod,
                                                       methods: List[Method], connection: Connection)
    extends RequestPhase[Auth] with RequestedState[Auth] with Terminated
  // 已请求异常
  case class RequestedError[Auth] private[socks5] (request: Request, auth: Auth, selected: AcceptableMethod,
                                                   methods: List[Method], connection: Connection, error: Throwable)
    extends RequestPhase[Auth] with RequestedState[Auth] with ErrorState

  // 已Connect
  case class Connected[Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                 request: Request, auth: Auth, selected: AcceptableMethod,
                                                 methods: List[Method], connection: Connection)
    extends ConnectedPhase[Auth, S] with RespondedSuccessState[Auth, S] with Active:
    private[socks5] def closed: ConnectClosed[Auth, S] =
      ConnectClosed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def closed(state: S): ConnectClosed[Auth, S] =
      ConnectClosed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def error(error: Throwable): Terminated =
      ConnectError(state, response, addressBytes, request, auth, selected, methods, connection, error)
  end Connected
  // Connect失败
  case class ConnectFailed[Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                       request: Request, auth: Auth, selected: AcceptableMethod,
                                                       methods: List[Method], connection: Connection)
    extends ConnectedPhase[Auth, S] with RespondedFailedState[Auth, S]
  // Connect关闭
  case class ConnectClosed[Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                       request: Request, auth: Auth, selected: AcceptableMethod,
                                                       methods: List[Method], connection: Connection)
    extends ConnectedPhase[Auth, S] with Closed[Auth, S]
  // Connect异常
  case class ConnectError[Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                    request: Request, auth: Auth, selected: AcceptableMethod,
                                                    methods: List[Method], connection: Connection, error: Throwable)
    extends ConnectedPhase[Auth, S] with ErrorState

  // 已Bind
  case class Bound[Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector, request: Request,
                                             auth: Auth, selected: AcceptableMethod, methods: List[Method],
                                             connection: Connection)
    extends BoundPhase[Auth, S] with RespondedSuccessState[Auth, S] with Active:
    private[socks5] def closed: BindClosed[Auth, S] =
      BindClosed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def closed(state: S): BindClosed[Auth, S] =
      BindClosed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def error(error: Throwable): Terminated =
      BindError(state, response, addressBytes, request, auth, selected, methods, connection, error)
  end Bound
  // Bind失败
  case class BindFailed[Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                    request: Request, auth: Auth, selected: AcceptableMethod,
                                                    methods: List[Method], connection: Connection)
    extends BoundPhase[Auth, S] with RespondedFailedState[Auth, S]
  // Bind关闭
  case class BindClosed[Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                    request: Request, auth: Auth, selected: AcceptableMethod,
                                                    methods: List[Method], connection: Connection)
    extends BoundPhase[Auth, S] with Closed[Auth, S]
  // Bind异常
  case class BindError[Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                 request: Request, auth: Auth, selected: AcceptableMethod,
                                                 methods: List[Method], connection: Connection, error: Throwable)
    extends BoundPhase[Auth, S] with ErrorState

  // 已UDPAssociate
  case class UDPAssociated[Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                     request: Request, auth: Auth, selected: AcceptableMethod,
                                                     methods: List[Method], connection: Connection)
    extends UDPAssociatedPhase[Auth, S] with RespondedSuccessState[Auth, S] with Active:
    private[socks5] def closed: UDPAssociateClosed[Auth, S] =
      UDPAssociateClosed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def closed(state: S): UDPAssociateClosed[Auth, S] =
      UDPAssociateClosed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def error(error: Throwable): Terminated =
      UDPAssociateError(state, response, addressBytes, request, auth, selected, methods, connection, error)
  end UDPAssociated
  // UDPAssociate失败
  case class UDPAssociateFailed[Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                            request: Request, auth: Auth, selected: AcceptableMethod,
                                                            methods: List[Method], connection: Connection)
    extends UDPAssociatedPhase[Auth, S] with RespondedFailedState[Auth, S]
  // UDPAssociate关闭
  case class UDPAssociateClosed[Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                            request: Request, auth: Auth, selected: AcceptableMethod,
                                                            methods: List[Method], connection: Connection)
    extends UDPAssociatedPhase[Auth, S] with Closed[Auth, S]
  // UDPAssociate异常
  case class UDPAssociateError[Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                         request: Request, auth: Auth, selected: AcceptableMethod,
                                                         methods: List[Method], connection: Connection, error: Throwable)
    extends UDPAssociatedPhase[Auth, S] with ErrorState
end State
