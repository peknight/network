package com.peknight.socks5.state

import com.peknight.auth.UserPassword
import com.peknight.socks.Connection
import com.peknight.socks5.auth.Method
import com.peknight.socks5.auth.Method.*
import com.peknight.socks5.auth.password.Status.Failure
import com.peknight.socks5.state.State.Terminated
import com.peknight.socks5.{Request, Response}
import scodec.bits.ByteVector

sealed trait State[F[_]]:
  def connection: Connection[F]
  def terminated: Boolean
  private[socks5] def error(error: Throwable): Terminated[F]
end State
object State:
  // 初始状态
  sealed trait InitialState[F[_]] extends State[F]
  // 协商状态
  sealed trait NegotiationState[F[_]] extends InitialState[F]:
    def methods: List[Method]
  end NegotiationState
  // 方法已选择状态
  sealed trait MethodSelectedState[F[_], +M <: Method] extends NegotiationState[F]:
    def selected: M
  end MethodSelectedState
  // 可接受方法已选择状态
  sealed trait AcceptableMethodSelectedState[F[_], +M <: AcceptableMethod] extends MethodSelectedState[F, M]
  // 需认证方法状态
  sealed trait AuthRequiredMethodState[F[_], +M <: AuthRequiredMethod] extends AcceptableMethodSelectedState[F, M]
  // 用户名密码方法状态
  sealed trait UsernamePasswordState[F[_]] extends AuthRequiredMethodState[F, UsernamePassword.type]:
    def selected: UsernamePassword.type = UsernamePassword
  end UsernamePasswordState
  // 认证状态
  sealed trait AuthenticationState[F[_], +M <: AcceptableMethod] extends AcceptableMethodSelectedState[F, M]
  // 已认证状态
  sealed trait AuthenticatedState[F[_], +Auth] extends AuthenticationState[F, AcceptableMethod]:
    def auth: Auth
  end AuthenticatedState
  // 请求状态
  sealed trait RequestState[F[_], +Auth] extends AuthenticatedState[F, Auth]
  // 已请求状态
  sealed trait RequestedState[F[_], +Auth] extends RequestState[F, Auth]:
    def request: Request
  end RequestedState
  // 已回复状态
  sealed trait RespondedState[F[_], +Auth, +S] extends RequestedState[F, Auth]:
    def response: Response
    def addressBytes: ByteVector
    def state: S
  end RespondedState
  sealed trait RespondedSuccessState[F[_], +Auth, +S] extends RespondedState[F, Auth, S]
  sealed trait RespondedFailedState[F[_], +Auth, +S] extends RespondedState[F, Auth, S] with Terminated[F]
  // Connect状态
  sealed trait ConnectedState[F[_], +Auth, +S] extends RespondedState[F, Auth, S]
  // Bind状态
  sealed trait BoundState[F[_], +Auth, +S] extends RespondedState[F, Auth, S]
  // UDPAssociate状态
  sealed trait UDPAssociatedState[F[_], +Auth, +S] extends RespondedState[F, Auth, S]

  // 正常关闭
  sealed trait Closed[F[_], +Auth, +S] extends RespondedSuccessState[F, Auth, S] with Terminated[F]

  // 运行状态
  sealed trait Active[F[_]] extends State[F]:
    def terminated: Boolean = false
  end Active

  // 结束状态
  sealed trait Terminated[F[_]] extends State[F]:
    def terminated: Boolean = true
    private[socks5] def error(error: Throwable): Terminated[F] = this
  end Terminated

  // 异常状态
  sealed trait ErrorState[F[_]] extends Terminated[F]:
    def error: Throwable
  end ErrorState

  // 初始阶段
  sealed trait InitialPhase[F[_]] extends InitialState[F]
  // 协商阶段
  sealed trait NegotiationPhase[F[_]] extends NegotiationState[F]
  // 认证阶段
  sealed trait AuthenticationPhase[F[_], +M <: AcceptableMethod] extends AuthenticationState[F, M]
  // 请求阶段
  sealed trait RequestPhase[F[_], +Auth] extends RequestState[F, Auth]
  sealed trait RespondedPhase[F[_], +Auth, +S] extends RespondedState[F, Auth, S]
  // Connect阶段
  sealed trait ConnectedPhase[F[_], +Auth, +S] extends RespondedPhase[F, Auth, S] with ConnectedState[F, Auth, S]
  // Bind阶段
  sealed trait BoundPhase[F[_], +Auth, +S] extends RespondedPhase[F, Auth, S] with BoundState[F, Auth, S]
  // UDPAssociate阶段
  sealed trait UDPAssociatedPhase[F[_], +Auth, +S] extends RespondedPhase[F, Auth, S] with UDPAssociatedState[F, Auth, S]

  // 初始
  case class Initial[F[_]] private[socks5] (connection: Connection[F]) extends InitialPhase[F] with Active[F]:
    private[socks5] def negotiating(methods: List[Method]): Negotiating[F] = Negotiating(methods, connection)
    private[socks5] def error(error: Throwable): Terminated[F] = InitialError(connection, error)
  end Initial

  // 初始异常
  case class InitialError[F[_]] private[socks5] (connection: Connection[F], error: Throwable)
    extends InitialPhase[F] with ErrorState[F]

  // 协商中
  case class Negotiating[F[_]] private[socks5] (methods: List[Method], connection: Connection[F])
    extends NegotiationPhase[F] with Active[F]:
    private[socks5] def noAuthenticationRequired[Auth](auth: Auth): Authenticated[F, Auth] =
      Authenticated(auth, NoAuthenticationRequired, methods, connection)
    private[socks5] def authRequiredMethod(selected: AuthRequiredMethod): AuthRequiredMethodSelected[F] =
        AuthRequiredMethodSelected(selected, methods, connection)
    private[socks5] def noAcceptableMethod: NoAcceptableMethod[F] = NoAcceptableMethod(methods, connection)
    private[socks5] def unsupportedMethod(selected: AcceptableMethod): UnsupportedMethod[F] =
      UnsupportedMethod(selected, methods, connection)
    private[socks5] def error(error: Throwable): Terminated[F] = NegotiationError(methods, connection, error)
  end Negotiating
  // 协商异常
  case class NegotiationError[F[_]] private[socks5] (methods: List[Method], connection: Connection[F], error: Throwable)
    extends NegotiationPhase[F] with ErrorState[F]
  // 没有可接受方法
  case class NoAcceptableMethod[F[_]] private[socks5] (methods: List[Method], connection: Connection[F])
    extends NegotiationPhase[F] with MethodSelectedState[F, Method.NoAcceptableMethod.type] with Terminated[F]:
    def selected: Method.NoAcceptableMethod.type = Method.NoAcceptableMethod
  end NoAcceptableMethod
  // 选择需认证方法
  case class AuthRequiredMethodSelected[F[_]] private[socks5](selected: AuthRequiredMethod, methods: List[Method],
                                                              connection: Connection[F])
    extends NegotiationPhase[F] with AuthRequiredMethodState[F, AuthRequiredMethod] with Active[F]:
    private[socks5] def passwordUnsafe(userPassword: UserPassword): UsernamePasswordAuthenticating[F] =
      UsernamePasswordAuthenticating(userPassword, methods, connection)
    private[socks5] def unsupportedMethod: UnsupportedMethod[F] = UnsupportedMethod(selected, methods, connection)
    private[socks5] def error(error: Throwable): Terminated[F] = error match
      case com.peknight.socks5.error.UnsupportedMethod(_) => UnsupportedMethod(selected, methods, connection)
      case _ => AuthenticationError(selected, methods, connection, error)
  end AuthRequiredMethodSelected
  // 不支持的方法
  case class UnsupportedMethod[F[_]] private[socks5] (selected: AcceptableMethod, methods: List[Method],
                                                      connection: Connection[F])
    extends NegotiationPhase[F] with MethodSelectedState[F, AcceptableMethod] with Terminated[F]

  // 认证通过
  case class Authenticated[F[_], Auth] private[socks5] (auth: Auth, selected: AcceptableMethod, methods: List[Method],
                                                        connection: Connection[F])
    extends AuthenticationPhase[F, AcceptableMethod] with AuthenticatedState[F, Auth] with Active[F]:
    private[socks5] def requested(request: Request): Requested[F, Auth] =
      Requested(request, auth, selected, methods, connection)
    private[socks5] def error(error: Throwable): Terminated[F] =
      RequestError(auth, selected, methods, connection, error)
  end Authenticated
  // 认证异常
  case class AuthenticationError[F[_]](selected: AcceptableMethod, methods: List[Method], connection: Connection[F],
                                       error: Throwable)
    extends AuthenticationPhase[F, AcceptableMethod] with ErrorState[F]
  // UsernamePassword认证中
  case class UsernamePasswordAuthenticating[F[_]] private[socks5] (userPassword: UserPassword, methods: List[Method],
                                                                   connection: Connection[F])
    extends AuthenticationPhase[F, UsernamePassword.type] with UsernamePasswordState[F] with Active[F]:
    private[socks5] def authenticated[Auth](auth: Auth): Authenticated[F, Auth] =
      Authenticated(auth, selected, methods, connection)
    private[socks5] def failed(status: Failure): UsernamePasswordFailed[F] =
      UsernamePasswordFailed(status, methods, connection)
    private[socks5] def error(error: Throwable): Terminated[F] =
      UsernamePasswordAuthenticationError(methods, connection, error)
  end UsernamePasswordAuthenticating
  // UsernamePassword认证失败
  case class UsernamePasswordFailed[F[_]] private[socks5] (status: Failure, methods: List[Method],
                                                           connection: Connection[F])
    extends AuthenticationPhase[F, UsernamePassword.type] with AuthRequiredMethodState[F, UsernamePassword.type]
      with UsernamePasswordState[F] with Terminated[F]
  // UsernamePassword认证异常
  case class UsernamePasswordAuthenticationError[F[_]] private[socks5] (methods: List[Method], connection: Connection[F],
                                                                        error: Throwable)
    extends AuthenticationPhase[F, UsernamePassword.type] with AuthRequiredMethodState[F, UsernamePassword.type]
      with UsernamePasswordState[F] with ErrorState[F]

  // 请求异常
  case class RequestError[F[_], Auth] private[socks5] (auth: Auth, selected: AcceptableMethod, methods: List[Method],
                                                    connection: Connection[F], error: Throwable)
    extends RequestPhase[F, Auth] with ErrorState[F]
  // 已请求
  case class Requested[F[_], Auth] private[socks5] (request: Request, auth: Auth, selected: AcceptableMethod,
                                                    methods: List[Method], connection: Connection[F])
    extends RequestPhase[F, Auth] with RequestedState[F, Auth] with Active[F]:
    private[socks5] def connected[S](response: Response, state: S, addressBytes: ByteVector): Connected[F, Auth, S] =
      Connected(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def bound[S](response: Response, state: S, addressBytes: ByteVector): Bound[F, Auth, S] =
      Bound(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def udpAssociated[S](response: Response, state: S, addressBytes: ByteVector)
    : UDPAssociated[F, Auth, S] =
      UDPAssociated(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def connectFailed[S](response: Response, state: S, addressBytes: ByteVector)
    : ConnectFailed[F, Auth, S] =
      ConnectFailed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def bindFailed[S](response: Response, state: S, addressBytes: ByteVector)
    : BindFailed[F, Auth, S] =
      BindFailed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def udpAssociateFailed[S](response: Response, state: S, addressBytes: ByteVector)
    : UDPAssociateFailed[F, Auth, S] =
      UDPAssociateFailed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def unsupportedCommand: UnsupportedCommand[F, Auth] =
      UnsupportedCommand(request, auth, selected, methods, connection)
    private[socks5] def error(error: Throwable): Terminated[F] =
      RequestedError(request, auth, selected, methods, connection, error)
  end Requested
  case class UnsupportedCommand[F[_], Auth] private[socks5] (request: Request, auth: Auth, selected: AcceptableMethod,
                                                          methods: List[Method], connection: Connection[F])
    extends RequestPhase[F, Auth] with RequestedState[F, Auth] with Terminated[F]
  // 已请求异常
  case class RequestedError[F[_], Auth] private[socks5] (request: Request, auth: Auth, selected: AcceptableMethod,
                                                         methods: List[Method], connection: Connection[F],
                                                         error: Throwable)
    extends RequestPhase[F, Auth] with RequestedState[F, Auth] with ErrorState[F]

  // 已Connect
  case class Connected[F[_], Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                       request: Request, auth: Auth, selected: AcceptableMethod,
                                                       methods: List[Method], connection: Connection[F])
    extends ConnectedPhase[F, Auth, S] with RespondedSuccessState[F, Auth, S] with Active[F]:
    private[socks5] def closed: ConnectClosed[F, Auth, S] =
      ConnectClosed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def closed(state: S): ConnectClosed[F, Auth, S] =
      ConnectClosed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def error(error: Throwable): Terminated[F] =
      ConnectError(state, response, addressBytes, request, auth, selected, methods, connection, error)
  end Connected
  // Connect失败
  case class ConnectFailed[F[_], Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                           request: Request, auth: Auth, selected: AcceptableMethod,
                                                           methods: List[Method], connection: Connection[F])
    extends ConnectedPhase[F, Auth, S] with RespondedFailedState[F, Auth, S]
  // Connect关闭
  case class ConnectClosed[F[_], Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                           request: Request, auth: Auth, selected: AcceptableMethod,
                                                           methods: List[Method], connection: Connection[F])
    extends ConnectedPhase[F, Auth, S] with Closed[F, Auth, S]
  // Connect异常
  case class ConnectError[F[_], Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                          request: Request, auth: Auth, selected: AcceptableMethod,
                                                          methods: List[Method], connection: Connection[F],
                                                          error: Throwable)
    extends ConnectedPhase[F, Auth, S] with ErrorState[F]

  // 已Bind
  case class Bound[F[_], Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                   request: Request, auth: Auth, selected: AcceptableMethod,
                                                   methods: List[Method], connection: Connection[F])
    extends BoundPhase[F, Auth, S] with RespondedSuccessState[F, Auth, S] with Active[F]:
    private[socks5] def closed: BindClosed[F, Auth, S] =
      BindClosed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def closed(state: S): BindClosed[F, Auth, S] =
      BindClosed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def error(error: Throwable): Terminated[F] =
      BindError(state, response, addressBytes, request, auth, selected, methods, connection, error)
  end Bound
  // Bind失败
  case class BindFailed[F[_], Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                        request: Request, auth: Auth, selected: AcceptableMethod,
                                                        methods: List[Method], connection: Connection[F])
    extends BoundPhase[F, Auth, S] with RespondedFailedState[F, Auth, S]
  // Bind关闭
  case class BindClosed[F[_], Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                        request: Request, auth: Auth, selected: AcceptableMethod,
                                                        methods: List[Method], connection: Connection[F])
    extends BoundPhase[F, Auth, S] with Closed[F, Auth, S]
  // Bind异常
  case class BindError[F[_], Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                       request: Request, auth: Auth, selected: AcceptableMethod,
                                                       methods: List[Method], connection: Connection[F],
                                                       error: Throwable)
    extends BoundPhase[F, Auth, S] with ErrorState[F]

  // 已UDPAssociate
  case class UDPAssociated[F[_], Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                           request: Request, auth: Auth, selected: AcceptableMethod,
                                                           methods: List[Method], connection: Connection[F])
    extends UDPAssociatedPhase[F, Auth, S] with RespondedSuccessState[F, Auth, S] with Active[F]:
    private[socks5] def closed: UDPAssociateClosed[F, Auth, S] =
      UDPAssociateClosed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def closed(state: S): UDPAssociateClosed[F, Auth, S] =
      UDPAssociateClosed(state, response, addressBytes, request, auth, selected, methods, connection)
    private[socks5] def error(error: Throwable): Terminated[F] =
      UDPAssociateError(state, response, addressBytes, request, auth, selected, methods, connection, error)
  end UDPAssociated
  // UDPAssociate失败
  case class UDPAssociateFailed[F[_], Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                                request: Request, auth: Auth, selected: AcceptableMethod,
                                                                methods: List[Method], connection: Connection[F])
    extends UDPAssociatedPhase[F, Auth, S] with RespondedFailedState[F, Auth, S]
  // UDPAssociate关闭
  case class UDPAssociateClosed[F[_], Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                                request: Request, auth: Auth, selected: AcceptableMethod,
                                                                methods: List[Method], connection: Connection[F])
    extends UDPAssociatedPhase[F, Auth, S] with Closed[F, Auth, S]
  // UDPAssociate异常
  case class UDPAssociateError[F[_], Auth, S] private[socks5] (state: S, response: Response, addressBytes: ByteVector,
                                                               request: Request, auth: Auth, selected: AcceptableMethod,
                                                               methods: List[Method], connection: Connection[F],
                                                               error: Throwable)
    extends UDPAssociatedPhase[F, Auth, S] with ErrorState[F]
end State
