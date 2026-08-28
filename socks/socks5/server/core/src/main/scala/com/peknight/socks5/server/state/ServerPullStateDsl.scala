package com.peknight.socks5.server.state

import cats.effect.{Async, Resource}
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
import fs2.text.utf8
import fs2.{Pipe, Stream}
import scodec.bits.ByteVector

import java.nio.charset.Charset
import java.time.LocalDateTime

/**
 * SOCKS5 服务端状态机专用的 [[BytePullStateErrorDsl]]：
 * 固定 `S = State`、`E = Terminated`，底层 `Throwable` 统一通过 `State.error` 提升。
 */
trait ServerPullStateDsl[F[_]] extends PullStateDsl[F]:

  def state[Auth, ConnectState, BindState, UDPAssociateState]
           (api: ServerApi[F, Auth, ConnectState, BindState, UDPAssociateState])
           (using Charset)(using Async[F]): Aux[State[F]] =
    val pullState: Aux[State[F]] =
      for
        _ <- negotiation[Auth](api.negotiationApi.negotiation)
        _ <- authentication[Auth](api.usernamePasswordApi.usernamePassword)(api.gssApiApi.gssApi,
          api.ianaAssignedApi.ianaAssigned, api.privateMethodApi.privateMethod)
        _ <- request[Auth, ConnectState, BindState, UDPAssociateState](api.connectApi.connect)(api.bindApi.bind)(
          api.udpAssociateApi.udpAssociate)
        _ <- established[Auth, ConnectState, BindState, UDPAssociateState](api.connectApi.tunnel)(api.bindApi.bound,
          api.udpAssociateApi.udpAssociated)
        state <- getS
      yield
        state
    pullState.attempt

  private def negotiation[Auth](f: Negotiating[F] => F[Either[NoAcceptableMethod.type | AuthRequiredMethod, Auth]])
  : Aux[State[F]] =
    val pullState: Aux[State[F]] =
      for
        initial <- typedS[Initial[F]]
        methods <- readNegotiation
        negotiating = initial.negotiating(methods)
        _ <- setS(negotiating)
        either <- liftF[Either[NoAcceptableMethod.type | AuthRequiredMethod, Auth]](f(negotiating)).attempt
        state = either match
          case Left(NoAcceptableMethod) => negotiating.noAcceptableMethod
          case Left(selected: AuthRequiredMethod) => negotiating.authRequiredMethod(selected)
          case Right(auth) => negotiating.noAuthenticationRequired(auth)
        _ <- setS(state)
      yield
        state
    pullState.outputS(encodeSelected)

  private def authentication[Auth](f: UsernamePasswordAuthenticating[F] => F[Either[Failure, Auth]])
                                  (gssApi: Aux[Auth], ianaAssigned: Aux[Auth], privateMethod: Aux[Auth])
                                  (using Charset): Aux[State[F]] =
    getS.flatMap {
      case AuthRequiredMethodSelected(GSSAPI, _, _) => gssApi.flatMap(_ => getS)
      case AuthRequiredMethodSelected(UsernamePassword, _, _) => passwordAuth[Auth](f)
      case AuthRequiredMethodSelected(IANAAssigned(_), _, _) => ianaAssigned.flatMap(_ => getS)
      case AuthRequiredMethodSelected(PrivateMethod(_), _, _) => privateMethod.flatMap(_ => getS)
      case state => pure[State[F]](state)
    }

  private def passwordAuth[Auth](f: UsernamePasswordAuthenticating[F] => F[Either[Failure, Auth]])(using Charset)
  : Aux[State[F]] =
    val pullState: Aux[State[F]] =
      for
        authRequiredMethodSelected <- typedS[AuthRequiredMethodSelected[F]]
        _ <- typed[UsernamePassword.type](authRequiredMethodSelected.selected)
        usernamePassword <- readPasswordAuth
        usernamePasswordAuthenticating = authRequiredMethodSelected.passwordUnsafe(usernamePassword)
        _ <- setS(usernamePasswordAuthenticating)
        either <- liftF[Either[Failure, Auth]](f(usernamePasswordAuthenticating)).attempt
        state = either match
          case Right(auth) => usernamePasswordAuthenticating.authenticated(auth)
          case Left(failure) => usernamePasswordAuthenticating.failed(failure)
        _ <- setS(state)
      yield
        state
    pullState.outputS(encodeStatus)

  private def request[Auth, ConnectState, BindState, UDPAssociateState]
                     (connectF: Requested[F, Auth] => F[(Response, ConnectState)])
                     (bindF: Requested[F, Auth] => F[(Response, BindState)])
                     (udpAssociateF: Requested[F, Auth] => F[(Response, UDPAssociateState)])
                     (using Charset): Aux[State[F]] =
    val pullState: Aux[State[F]] =
      for
        authenticated <- typedS[Authenticated[F, Auth]]
        request <- readRequest
        requested = authenticated.requested(request)
        _ <- setS(requested)
        state <- request.command match
          case CONNECT => handleRequest[Auth, ConnectState, Connected[F, Auth, ConnectState], ConnectFailed[F, Auth, ConnectState]](connectF)(_.connected(_, _, _))(_.connectFailed(_, _, _))
          case BIND => handleRequest[Auth, BindState, Bound[F, Auth, BindState], BindFailed[F, Auth, BindState]](bindF)(_.bound(_, _, _))(_.bindFailed(_, _, _))
          case UDP_ASSOCIATE => handleRequest[Auth, UDPAssociateState, UDPAssociated[F, Auth, UDPAssociateState], UDPAssociateFailed[F, Auth, UDPAssociateState]](udpAssociateF)(_.udpAssociated(_, _, _))(_.udpAssociateFailed(_, _, _))
      yield
        state
    pullState.outputS(encodeResponse)

  private def handleRequest[Auth, S, Success <: RespondedSuccessState[F, Auth, S], Failed <: RespondedFailedState[F, Auth, S]]
                           (f: Requested[F, Auth] => F[(Response, S)])
                           (success: (Requested[F, Auth], Response, S, ByteVector) => Success)
                           (failed: (Requested[F, Auth], Response, S, ByteVector) => Failed)
                           (using Charset): Aux[State[F]] =
    for
      requested <- typedS[Requested[F, Auth]]
      (response, s) <- liftF[(Response, S)](f(requested)).attempt
      addressBytes <- liftET[ByteVector](encodeAddress(response.address))
      state =
        if response.reply.success then success(requested, response, s, addressBytes)
        else failed(requested, response, s, addressBytes)
      _ <- setS(state)
    yield
      state

  private def established[Auth, ConnectState, BindState, UDPAssociateState]
                         (tunnel: Connected[F, Auth, ConnectState] => Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])])
                         (bound: Aux[Unit], udpAssociated: Aux[Unit])(using Async[F]): Aux[Unit] =
    getS.flatMap {
      case _: Connected[?, ?, ?] => connected[Auth, ConnectState](tunnel)
      case _: Bound[?, ?, ?] => bound
      case _: UDPAssociated[?, ?, ?] => udpAssociated
      case state => liftT[Unit](WrongClassTag[RespondedSuccessState[?, ?, ?]](state))
    }

  private def connected[Auth, ConnectState](tunnel: Connected[F, Auth, ConnectState] => Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])])
                                           (using Async[F]): Aux[Unit] =
    for
      connected <- typedS[Connected[F, Auth, ConnectState]]
      _ <- pipe(input => Stream
        .resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])](tunnel(connected))
        .flatMap((send, receive) => receive
          .observe(in => in.through(utf8.decode[F]).evalTap(s => Async[F].delay(println(s"server receive: $s"))).drain)
          .onFinalize(connected.connection.endOfOutput)
          .onFinalize(Async[F].delay(println(s"${LocalDateTime.now} server connected pipe receive finalized")))
          .merge(input
            .observe(in => in.through(utf8.decode[F]).evalTap(s => Async[F].delay(println(s"server input: $s"))).drain)
            .onFinalize(connected.connection.endOfInput)
            .onFinalize(Async[F].delay(println(s"${LocalDateTime.now} server connected pipe input finalized")))
            .through(send)
            .onFinalize(Async[F].delay(println(s"${LocalDateTime.now} server connected pipe send finalized")))
            .drain)
          .onFinalize(Async[F].delay(println(s"${LocalDateTime.now} server connected pipe merge finalized")))
        )
        .onFinalize(Async[F].delay(println(s"${LocalDateTime.now} server connected pipe finalized")))
      ).attempt
      _ <- setS(connected.closed)
    yield
      ()

  private def readNegotiation: Aux[List[Method]] =
    for
      _ <- readSocks5Version
      methods <- readMethods
    yield
      methods

  private def readMethods: Aux[List[Method]] =
    mapSizedBytes[List[Method]](_.map(Method.apply).toList)(MethodsEof)

  private def readPasswordAuth(using Charset): Aux[UPassword] =
    for
      _ <- readPasswordVersion
      user <- readSizedString(UsernameEof).map(User.apply)
      password <- readSizedString(PasswordEof).map(Password.apply)
    yield
      UPassword(user, password)

  private def readRequest(using Charset): Aux[Request] =
    for
      _ <- readSocks5Version
      command <- readCommand
      _ <- readReserved
      (address, _) <- readAddress
      port <- readPort
    yield
      Request(command, address, port)

  private def readCommand: Aux[Command] =
    parse1[Command](cmd =>Command.values.find(_.code == cmd).toRight(UnsupportedCommand(cmd)))(CommandEof)

  private def encodeSelected(state: State[F]): ByteVector =
    val code = state match
      case s: MethodSelectedState[F, Method] => s.selected.code
      case s => NoAcceptableMethod.code
    ByteVector(socks5.code, code)

  private def encodeStatus(state: State[F]): ByteVector =
    val status: Status = state match
      case _: Authenticated[?, ?] => Success
      case UsernamePasswordFailed(status, _, _) => status
      case _ => Failure.default
    ByteVector(version1.code, status.code)

  private def encodeResponse(state: State[F]): ByteVector =
    val (response, addressBytes) = state match
      case s: RespondedState[?, ?, ?] => (s.response, s.addressBytes)
      case _ => (toResponse(state), encodeIpAddress(Response.defaultHost))
    val addressTypeCode = AddressType.fromHost(response.address).code
    ByteVector(socks5.code, response.reply.code, Reserved.code, addressTypeCode) ++
      addressBytes ++
      encodePort(response.port)
end ServerPullStateDsl
object ServerPullStateDsl:
  private class ServerPullStateDsl[F[_]] extends com.peknight.socks5.server.state.ServerPullStateDsl[F]
  def apply[F[_]]: com.peknight.socks5.server.state.ServerPullStateDsl[F] = new ServerPullStateDsl[F]
end ServerPullStateDsl
