package com.peknight.socks5.server.state

import cats.Applicative
import cats.effect.{Concurrent, Resource}
import cats.syntax.applicative.*
import com.peknight.auth.{Password, User, UserPassword as UPassword}
import com.peknight.cats.instances.eitherT.given
import com.peknight.error.std.WrongClassTag
import com.peknight.socks.SocksVersion.socks5
import com.peknight.socks5.*
import com.peknight.socks5.Command.{BIND, CONNECT, UDP_ASSOCIATE}
import com.peknight.socks5.auth.Method
import com.peknight.socks5.auth.Method.*
import com.peknight.socks5.auth.password.PasswordVersion.version1
import com.peknight.socks5.auth.password.Status
import com.peknight.socks5.auth.password.Status.{Failure, Success}
import com.peknight.socks5.error.UnsupportedCommand
import com.peknight.socks5.server.api.ServerApi
import com.peknight.socks5.server.error.*
import com.peknight.socks5.state.State.{NoAcceptableMethod as _, UnsupportedCommand as _, *}
import com.peknight.socks5.state.{PullStateDsl, State}
import fs2.{Pipe, Stream}
import scodec.bits.ByteVector

import java.nio.charset.Charset

/**
 * SOCKS5 服务端状态机专用的 [[BytePullStateErrorDsl]]：
 * 固定 `S = State`、`E = Terminated`，底层 `Throwable` 统一通过 `State.error` 提升。
 */
object ServerPullState extends PullStateDsl:

  def apply[F[_], Auth, ConnectState, BindState, UDPAssociateState]
           (api: ServerApi[F, Auth, ConnectState, BindState, UDPAssociateState])
           (using Charset)(using Concurrent[F]): Aux[F, State] =
    val pullState: Aux[F, State] =
      for
        _ <- negotiation[F, Auth](api.negotiationApi.negotiation)
        _ <- authentication[F, Auth](api.usernamePasswordApi.usernamePassword)(api.gssApiApi.gssApi,
          api.ianaAssignedApi.ianaAssigned, api.privateMethodApi.privateMethod)
        _ <- request[F, Auth, ConnectState, BindState, UDPAssociateState](api.connectApi.connect)(api.bindApi.bind)(
          api.udpAssociateApi.udpAssociate)
        _ <- established[F, Auth, ConnectState, BindState, UDPAssociateState](api.connectApi.tunnel)(api.bindApi.bound,
          api.udpAssociateApi.udpAssociated)
        state <- getS[F]
      yield
        state
    pullState.attempt

  def unsupportedCommand[F[_] : Applicative, Auth, S](state: Requested[Auth], s: S): F[(Response, S)] =
    (Response.unsupportedCommand, s).pure[F]

  private def negotiation[F[_], Auth](f: Negotiating => F[Either[NoAcceptableMethod.type | AuthRequiredMethod, Auth]])
  : Aux[F, State] =
    val pullState: Aux[F, State] =
      for
        initial <- typedS[F, Initial]
        methods <- readNegotiation[F]
        negotiating = initial.negotiating(methods)
        _ <- setS[F](negotiating)
        either <- liftF[F, Either[NoAcceptableMethod.type | AuthRequiredMethod, Auth]](f(negotiating)).attempt
        state = either match
          case Left(NoAcceptableMethod) => negotiating.noAcceptableMethod
          case Left(selected: AuthRequiredMethod) => negotiating.authRequiredMethod(selected)
          case Right(auth) => negotiating.noAuthenticationRequired(auth)
        _ <- setS[F](state)
      yield
        state
    pullState.outputS(encodeSelected)

  private def authentication[F[_], Auth](f: UsernamePasswordAuthenticating => F[Either[Failure, Auth]])
                                        (gssApi: Aux[F, Auth], ianaAssigned: Aux[F, Auth], privateMethod: Aux[F, Auth])
                                        (using Charset): Aux[F, State] =
    getS[F].flatMap {
      case AuthRequiredMethodSelected(GSSAPI, _, _) => gssApi.flatMap(_ => getS[F])
      case AuthRequiredMethodSelected(UsernamePassword, _, _) => passwordAuth[F, Auth](f)
      case AuthRequiredMethodSelected(IANAAssigned(_), _, _) => ianaAssigned.flatMap(_ => getS[F])
      case AuthRequiredMethodSelected(PrivateMethod(_), _, _) => privateMethod.flatMap(_ => getS[F])
      case state => pure[F, State](state)
    }

  private def passwordAuth[F[_], Auth](f: UsernamePasswordAuthenticating => F[Either[Failure, Auth]])(using Charset)
  : Aux[F, State] =
    val pullState: Aux[F, State] =
      for
        authRequiredMethodSelected <- typedS[F, AuthRequiredMethodSelected]
        _ <- typed[F, UsernamePassword.type](authRequiredMethodSelected.selected)
        usernamePassword <- readPasswordAuth[F]
        usernamePasswordAuthenticating = authRequiredMethodSelected.passwordUnsafe(usernamePassword)
        _ <- setS[F](usernamePasswordAuthenticating)
        either <- liftF[F, Either[Failure, Auth]](f(usernamePasswordAuthenticating)).attempt
        state = either match
          case Right(auth) => usernamePasswordAuthenticating.authenticated(auth)
          case Left(failure) => usernamePasswordAuthenticating.failed(failure)
        _ <- setS[F](state)
      yield
        state
    pullState.outputS(encodeStatus)

  private def request[F[_], Auth, ConnectState, BindState, UDPAssociateState]
                     (connectF: Requested[Auth] => F[(Response, ConnectState)])
                     (bindF: Requested[Auth] => F[(Response, BindState)])
                     (udpAssociateF: Requested[Auth] => F[(Response, UDPAssociateState)])
                     (using Charset): Aux[F, State] =
    val pullState: Aux[F, State] =
      for
        authenticated <- typedS[F, Authenticated[Auth]]
        request <- readRequest[F]
        requested = authenticated.requested(request)
        _ <- setS[F](requested)
        state <- request.command match
          case CONNECT => handleRequest[F, Auth, ConnectState, Connected[Auth, ConnectState], ConnectFailed[Auth, ConnectState]](connectF)(_.connected(_, _, _))(_.connectFailed(_, _, _))
          case BIND => handleRequest[F, Auth, BindState, Bound[Auth, BindState], BindFailed[Auth, BindState]](bindF)(_.bound(_, _, _))(_.bindFailed(_, _, _))
          case UDP_ASSOCIATE => handleRequest[F, Auth, UDPAssociateState, UDPAssociated[Auth, UDPAssociateState], UDPAssociateFailed[Auth, UDPAssociateState]](udpAssociateF)(_.udpAssociated(_, _, _))(_.udpAssociateFailed(_, _, _))
      yield
        state
    pullState.outputS(encodeResponse)

  private def handleRequest[F[_], Auth, S, Success <: RespondedSuccessState[Auth, S], Failed <: RespondedFailedState[Auth, S]]
                           (f: Requested[Auth] => F[(Response, S)])
                           (success: (Requested[Auth], Response, S, ByteVector) => Success)
                           (failed: (Requested[Auth], Response, S, ByteVector) => Failed)
                           (using Charset): Aux[F, State] =
    for
      requested <- typedS[F, Requested[Auth]]
      (response, s) <- liftF[F, (Response, S)](f(requested)).attempt
      addressBytes <- liftET[F, ByteVector](encodeAddress(response.address))
      state =
        if response.reply.success then success(requested, response, s, addressBytes)
        else failed(requested, response, s, addressBytes)
      _ <- setS[F](state)
    yield
      state

  private def established[F[_]: Concurrent, Auth, ConnectState, BindState, UDPAssociateState]
                         (tunnel: Connected[Auth, ConnectState] => Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])])
                         (bound: Aux[F, Unit], udpAssociated: Aux[F, Unit])
  : Aux[F, Unit] =
    getS[F].flatMap {
      case _: Connected[?, ?] => connected[F, Auth, ConnectState](tunnel)
      case _: Bound[?, ?] => bound
      case _: UDPAssociated[?, ?] => udpAssociated
      case state => liftT[F, Unit](WrongClassTag[RespondedSuccessState[?, ?]](state))
    }

  private def connected[F[_]: Concurrent, Auth, ConnectState](
    tunnel: Connected[Auth, ConnectState] => Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]
  ): Aux[F, Unit] =
    for
      connected <- typedS[F, Connected[Auth, ConnectState]]
      _ <- pipe[F](in => Stream
        .resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])](tunnel(connected))
        .flatMap((send, receive) => receive.merge(in.through(send).drain))
      ).attempt
      _ <- setS[F](connected.closed)
    yield
      ()

  private def readNegotiation[F[_]]: Aux[F, List[Method]] =
    for
      _ <- readSocks5Version[F]
      methods <- readMethods[F]
    yield
      methods

  private def readMethods[F[_]]: Aux[F, List[Method]] =
    mapSizedBytes[F, List[Method]](_.map(Method.apply).toList)(MethodsEof)

  private def readPasswordAuth[F[_]](using Charset): Aux[F, UPassword] =
    for
      _ <- readPasswordVersion[F]
      user <- readSizedString[F](UsernameEof).map(User.apply)
      password <- readSizedString[F](PasswordEof).map(Password.apply)
    yield
      UPassword(user, password)

  private def readRequest[F[_]](using Charset): Aux[F, Request] =
    for
      _ <- readSocks5Version[F]
      command <- readCommand[F]
      _ <- readReserved[F]
      (address, _) <- readAddress[F]
      port <- readPort[F]
    yield
      Request(command, address, port)

  private def readCommand[F[_]]: Aux[F, Command] =
    parse1[F, Command](cmd =>
      Command.values.find(_.code == cmd).toRight(UnsupportedCommand(cmd))
    )(CommandEof)

  private def encodeSelected(state: State): ByteVector =
    val code = state match
      case s: MethodSelectedState[Method] => s.selected.code
      case s => NoAcceptableMethod.code
    ByteVector(socks5.code, code)

  private def encodeStatus(state: State): ByteVector =
    val status: Status = state match
      case _: Authenticated[?] => Success
      case UsernamePasswordFailed(status, _, _) => status
      case _ => Failure.default
    ByteVector(version1.code, status.code)

  private def encodeResponse(state: State): ByteVector =
    val (response, addressBytes) = state match
      case s: RespondedState[?, ?] => (s.response, s.addressBytes)
      case _ => (toResponse(state), encodeIpAddress(Response.defaultHost))
    val addressTypeCode = AddressType.fromHost(response.address).code
    ByteVector(socks5.code, response.reply.code, Reserved.code, addressTypeCode) ++
      addressBytes ++
      encodePort(response.port)
end ServerPullState
