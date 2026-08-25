package com.peknight.socks5.client.state

import com.peknight.socks.Connection
import com.peknight.socks5.Command
import com.peknight.socks5.Response
import com.peknight.socks5.auth.Method
import com.peknight.socks5.auth.Method.*
import com.peknight.socks5.auth.password.Status.Failure
import scodec.bits.ByteVector

import State.Terminated

sealed trait State:
  def connection: Connection
  def terminated: Boolean
  private[socks5] def error(error: Throwable): Terminated
end State

object State:
  // ===== marker 层次 =====
  // 初始状态
  sealed trait InitialState extends State
  // 方法已选择状态（server 选定的方法，线上读到）
  sealed trait MethodSelectedState[+M <: Method] extends InitialState:
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
  // 已回复状态（server 的 response 已读到）
  sealed trait RespondedState[+Auth] extends AuthenticatedState[Auth]:
    def command: Command
    def response: Response
    def addressBytes: ByteVector
  end RespondedState
  sealed trait RespondedSuccessState[+Auth, +S] extends RespondedState[Auth]:
    def state: S
  end RespondedSuccessState
  sealed trait RespondedFailedState[+Auth] extends RespondedState[Auth] with Terminated

  // 运行/结束/异常三态
  sealed trait Active extends State:
    def terminated: Boolean = false
  end Active
  sealed trait Terminated extends State:
    def terminated: Boolean = true
    private[socks5] def error(error: Throwable): Terminated = this
  end Terminated
  sealed trait ErrorState extends Terminated:
    def error: Throwable
  end ErrorState

  // phase 家族（供穷尽匹配用）
  sealed trait InitialPhase extends InitialState
  sealed trait MethodSelectedPhase[+M <: Method] extends MethodSelectedState[M]
  sealed trait AuthenticationPhase[+M <: AcceptableMethod] extends AuthenticationState[M]
  sealed trait RespondedPhase[+Auth] extends RespondedState[Auth]

  // ===== 初始 =====
  case class Initial private[socks5] (connection: Connection) extends InitialPhase with Active:
    private[socks5] def methodSelected(selected: AcceptableMethod): MethodSelected =
      MethodSelected(selected, connection)
    private[socks5] def noAcceptableMethod: NoAcceptableMethod = NoAcceptableMethod(connection)
    private[socks5] def error(error: Throwable): Terminated = InitialError(connection, error)
  end Initial

  // 初始异常
  case class InitialError private[socks5] (connection: Connection, error: Throwable)
    extends InitialPhase with ErrorState

  // 没有可接受方法（server 回复 0xFF）
  case class NoAcceptableMethod private[socks5] (connection: Connection)
    extends InitialPhase with MethodSelectedState[Method.NoAcceptableMethod.type] with Terminated:
    def selected: Method.NoAcceptableMethod.type = Method.NoAcceptableMethod
  end NoAcceptableMethod

  // ===== 方法已选择（线上读到的 server 决定） =====
  case class MethodSelected private[socks5] (selected: AcceptableMethod, connection: Connection)
    extends MethodSelectedPhase[AcceptableMethod] with AcceptableMethodSelectedState[AcceptableMethod] with Active:
    private[socks5] def authenticated[Auth](auth: Auth): Authenticated[Auth] =
      Authenticated(auth, selected, connection)
    private[socks5] def usernamePasswordAuthenticating: UsernamePasswordAuthenticating =
      UsernamePasswordAuthenticating(connection)
    private[socks5] def unsupportedMethod: UnsupportedMethod = UnsupportedMethod(selected, connection)
    private[socks5] def error(error: Throwable): Terminated = MethodSelectionError(selected, connection, error)
  end MethodSelected

  // 方法选择异常
  case class MethodSelectionError private[socks5] (selected: AcceptableMethod, connection: Connection, error: Throwable)
    extends MethodSelectedPhase[AcceptableMethod] with ErrorState
  // 不支持的方法（server 选了客户端未声明的方法）
  case class UnsupportedMethod private[socks5] (selected: AcceptableMethod, connection: Connection)
    extends MethodSelectedPhase[AcceptableMethod] with Terminated

  // ===== 用户名密码认证中 =====
  case class UsernamePasswordAuthenticating private[socks5] (connection: Connection)
    extends AuthenticationPhase[UsernamePassword.type] with UsernamePasswordState with Active:
    private[socks5] def authenticated[Auth](auth: Auth): Authenticated[Auth] =
      Authenticated(auth, selected, connection)
    private[socks5] def failed(status: Failure): UsernamePasswordFailed =
      UsernamePasswordFailed(status, connection)
    private[socks5] def error(error: Throwable): Terminated =
      UsernamePasswordAuthenticationError(connection, error)
  end UsernamePasswordAuthenticating

  // 用户名密码认证失败
  case class UsernamePasswordFailed private[socks5] (status: Failure, connection: Connection)
    extends AuthenticationPhase[UsernamePassword.type] with AuthRequiredMethodState[UsernamePassword.type]
      with UsernamePasswordState with Terminated
  // 用户名密码认证异常
  case class UsernamePasswordAuthenticationError private[socks5] (connection: Connection, error: Throwable)
    extends AuthenticationPhase[UsernamePassword.type] with AuthRequiredMethodState[UsernamePassword.type]
      with UsernamePasswordState with ErrorState

  // ===== 已认证 =====
  case class Authenticated[Auth] private[socks5] (auth: Auth, selected: AcceptableMethod, connection: Connection)
    extends AuthenticationPhase[AcceptableMethod] with AuthenticatedState[Auth] with Active:
    private[socks5] def responded[S](command: Command, response: Response, addressBytes: ByteVector, state: S): State =
      if response.reply.success then Responded(command, state, response, addressBytes, auth, selected, connection)
      else ReplyFailed(command, response, addressBytes, auth, selected, connection)
    private[socks5] def error(error: Throwable): Terminated = ResponseError(auth, selected, connection, error)
  end Authenticated

  // 读回复异常
  case class ResponseError[Auth] private[socks5] (auth: Auth, selected: AcceptableMethod, connection: Connection,
                                                  error: Throwable)
    extends AuthenticationPhase[AcceptableMethod] with AuthenticatedState[Auth] with ErrorState

  // ===== 已回复（统一三命令，用 command 字段区分） =====
  // 回复成功
  case class Responded[Auth, S] private[socks5] (command: Command, state: S, response: Response,
                                                 addressBytes: ByteVector, auth: Auth, selected: AcceptableMethod,
                                                 connection: Connection)
    extends RespondedPhase[Auth] with RespondedSuccessState[Auth, S] with Active:
    private[socks5] def closed: Closed[Auth, S] =
      Closed(command, state, response, addressBytes, auth, selected, connection)
    private[socks5] def error(error: Throwable): Terminated =
      ResponseHandlingError(command, state, response, addressBytes, auth, selected, connection, error)
  end Responded

  // server 回复失败（协议级终止，不是异常）
  case class ReplyFailed[Auth] private[socks5] (command: Command, response: Response, addressBytes: ByteVector,
                                                auth: Auth, selected: AcceptableMethod, connection: Connection)
    extends RespondedPhase[Auth] with RespondedFailedState[Auth]

  // 正常关闭
  case class Closed[Auth, S] private[socks5] (command: Command, state: S, response: Response, addressBytes: ByteVector,
                                              auth: Auth, selected: AcceptableMethod, connection: Connection)
    extends RespondedPhase[Auth] with RespondedSuccessState[Auth, S] with Terminated

  // 隧道处理异常
  case class ResponseHandlingError[Auth, S] private[socks5] (command: Command, state: S, response: Response,
                                                             addressBytes: ByteVector, auth: Auth,
                                                             selected: AcceptableMethod, connection: Connection,
                                                             error: Throwable)
    extends RespondedPhase[Auth] with RespondedSuccessState[Auth, S] with ErrorState
end State
