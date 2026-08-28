package com.peknight.socks5.client.state

import cats.effect.{Async, Resource}
import com.comcast.ip4s.{Ipv4Address, Ipv6Address}
import com.peknight.auth.UserPassword
import com.peknight.cats.instances.eitherT.given
import com.peknight.error.Error
import com.peknight.error.std.WrongClassTag
import com.peknight.socks.SocksVersion.socks5
import com.peknight.socks5.*
import com.peknight.socks5.Command.{BIND, CONNECT, UDP_ASSOCIATE}
import com.peknight.socks5.auth.Method
import com.peknight.socks5.auth.Method.*
import com.peknight.socks5.auth.password.PasswordVersion.version1
import com.peknight.socks5.auth.password.Status
import com.peknight.socks5.auth.password.Status.{Failure, Success}
import com.peknight.socks5.client.api.ClientApi
import com.peknight.socks5.client.error.{MethodEof, ReplyEof, StatusEof}
import com.peknight.socks5.state.State.{NoAcceptableMethod as _, *}
import com.peknight.socks5.state.{PullStateDsl, State}
import fs2.text.utf8
import fs2.{Pipe, Stream}
import scodec.bits.ByteVector

import java.nio.charset.Charset
import java.time.LocalDateTime

trait ClientPullStateDsl[F[_]] extends PullStateDsl[F]:

  def state[Auth, ConnectState, BindState, UDPAssociateState]
           (api: ClientApi[F, Auth, ConnectState, BindState, UDPAssociateState])
           (using Charset)(using Async[F]): Aux[State[F]] =
    val pullState: Aux[State[F]] =
      for
        _ <- negotiation[Auth](api.negotiationApi.negotiation)(api.negotiationApi.noAuthenticationRequired)
        _ <- authentication[Auth](api.usernamePasswordApi.usernamePassword)(api.usernamePasswordApi.authenticated)(
          api.gssApiApi.gssApi, api.ianaAssignedApi.ianaAssigned, api.privateMethodApi.privateMethod)
        _ <- request[Auth, ConnectState, BindState, UDPAssociateState](api.requestApi.request)(
          api.connectApi.connect)(api.bindApi.bind)(api.udpAssociateApi.udpAssociate)
        _ <- established[Auth, ConnectState, BindState, UDPAssociateState](api.connectApi.tunnel)(api.bindApi.bound,
          api.udpAssociateApi.udpAssociated)
        state <- getS
      yield
        state
    pullState.attempt

  private def negotiation[Auth](f: Initial[F] => F[List[Method]])(noAuthenticationRequired: Negotiating[F] => F[Auth])
  : Aux[State[F]] =
    for
      initial <- typedS[Initial[F]]
      methods <- liftF[List[Method]](f(initial)).attempt
      negotiating = initial.negotiating(methods)
      _ <- setS(negotiating)
      _ <- output(encodeMethods(methods))
      selected <- readNegotiation
      state <- selected match
        case NoAcceptableMethod => pure[State[F]](negotiating.noAcceptableMethod)
        case NoAuthenticationRequired =>
          liftF[Auth](noAuthenticationRequired(negotiating)).attempt
            .map(negotiating.noAuthenticationRequired)
        case authRequiredMethod: AuthRequiredMethod =>
          pure[State[F]](negotiating.authRequiredMethod(authRequiredMethod))
      _ <- setS(state)
    yield
      state

  private def authentication[Auth](f: AuthRequiredMethodSelected[F] => F[UserPassword])
                                  (authenticated: UsernamePasswordAuthenticating[F] => F[Auth])
                                  (gssApi: Aux[Auth], ianaAssigned: Aux[Auth], privateMethod: Aux[Auth])
                                  (using Charset): Aux[State[F]] =
    getS.flatMap {
      case AuthRequiredMethodSelected(GSSAPI, _, _) => gssApi.flatMap(_ => getS)
      case AuthRequiredMethodSelected(UsernamePassword, _, _) =>
        passwordAuth[Auth](f)(authenticated)
      case AuthRequiredMethodSelected(IANAAssigned(_), _, _) => ianaAssigned.flatMap(_ => getS)
      case AuthRequiredMethodSelected(PrivateMethod(_), _, _) => privateMethod.flatMap(_ => getS)
      case state => pure[State[F]](state)
    }

  private def passwordAuth[Auth](f: AuthRequiredMethodSelected[F] => F[UserPassword])
                                (authenticated: UsernamePasswordAuthenticating[F] => F[Auth])
                                (using Charset): Aux[State[F]] =
    for
      authRequiredMethodSelected <- typedS[AuthRequiredMethodSelected[F]]
      _ <- typed[UsernamePassword.type](authRequiredMethodSelected.selected)
      userPassword <- liftF[UserPassword](f(authRequiredMethodSelected)).attempt
      usernamePasswordAuthenticating = authRequiredMethodSelected.passwordUnsafe(userPassword)
      _ <- setS(usernamePasswordAuthenticating)
      bytes <- liftET[ByteVector](encodeUserPassword(userPassword))
      _ <- output(bytes)
      status <- readStatus
      state <- status match
        case Success =>
          liftF[Auth](authenticated(usernamePasswordAuthenticating)).attempt
            .map(usernamePasswordAuthenticating.authenticated)
        case failure@Failure(_) => pure[State[F]](usernamePasswordAuthenticating.failed(failure))
      _ <- setS(state)
    yield
      state

  private def request[Auth, ConnectState, BindState, UDPAssociateState]
                     (f: Authenticated[F, Auth] => F[Request])
                     (connectF: (Requested[F, Auth], Response) => F[ConnectState])
                     (bindF: (Requested[F, Auth], Response) => F[BindState])
                     (udpAssociateF: (Requested[F, Auth], Response) => F[UDPAssociateState])
                     (using Charset): Aux[State[F]] =
    for
      authenticated <- typedS[Authenticated[F, Auth]]
      request <- liftF[Request](f(authenticated)).attempt
      requested = authenticated.requested(request)
      _ <- setS(requested)
      bytes <- liftET[ByteVector](encodeRequest(request))
      _ <- output(bytes)
      (response, addressBytes) <- readResponse
      state <- request.command match
        case CONNECT => handleRequest[Auth, ConnectState, Connected[F, Auth, ConnectState], ConnectFailed[F, Auth, ConnectState]](response)(connectF)(_.connected(_, _, addressBytes))(_.connectFailed(_, _, addressBytes))
        case BIND => handleRequest[Auth, BindState, Bound[F, Auth, BindState], BindFailed[F, Auth, BindState]](response)(bindF)(_.bound(_, _, addressBytes))(_.bindFailed(_, _, addressBytes))
        case UDP_ASSOCIATE => handleRequest[Auth, UDPAssociateState, UDPAssociated[F, Auth, UDPAssociateState], UDPAssociateFailed[F, Auth, UDPAssociateState]](response)(udpAssociateF)(_.udpAssociated(_, _, addressBytes))(_.udpAssociateFailed(_, _, addressBytes))
    yield
      state

  private def handleRequest[Auth, S, Success <: RespondedSuccessState[F, Auth, S], Failed <: RespondedFailedState[F, Auth, S]]
                           (response: Response)
                           (f: (Requested[F, Auth], Response) => F[S])
                           (success: (Requested[F, Auth], Response, S) => Success)
                           (failed: (Requested[F, Auth], Response, S) => Failed)
                           (using Charset): Aux[State[F]] =
    for
      requested <- typedS[Requested[F, Auth]]
      s <- liftF[S](f(requested, response)).attempt
      state =
        if response.reply.success then success(requested, response, s)
        else failed(requested, response, s)
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
      _ <- pipe(output => Stream
        .resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])](tunnel(connected))
        .flatMap((publish, input) => input
          .observe(in => in.through(utf8.decode[F]).evalTap(s => Async[F].delay(println(s"${LocalDateTime.now} client input: $s"))).drain)
          .onFinalize(connected.connection.endOfOutput)
          .onFinalize(Async[F].delay(println(s"${LocalDateTime.now} client connected pipe input finalized")))
          .merge(output
            .observe(in => in.through(utf8.decode[F]).evalTap(s => Async[F].delay(println(s"${LocalDateTime.now} client output: $s"))).drain)
            .onFinalize(connected.connection.endOfInput)
            .onFinalize(Async[F].delay(println(s"${LocalDateTime.now} client connected pipe output finalized")))
            .through(publish)
            .onFinalize(Async[F].delay(println(s"${LocalDateTime.now} client connected pipe publish finalized")))
            .drain)
          .onFinalize(Async[F].delay(println(s"${LocalDateTime.now} client connected pipe merge finalized")))
        )
        .onFinalize(Async[F].delay(println(s"${LocalDateTime.now} client connected pipe finalized")))
      ).attempt
      _ <- setS(connected.closed)
    yield
      ()

  private def readNegotiation: Aux[Method] =
    for
      _ <- readSocks5Version
      method <- readMethod
    yield
      method

  private def readMethod: Aux[Method] =
    map1[Method](Method.apply)(MethodEof)

  private def readStatus: Aux[Status] =
    for
      _ <- readPasswordVersion
      status <- map1[Status](Status.apply)(StatusEof)
    yield
      status

  private def readResponse(using Charset): Aux[(Response, ByteVector)] =
    for
      _ <- readSocks5Version
      reply <- readReply
      _ <- readReserved
      (address, addressBytes) <- readAddress
      port <- readPort
    yield
      (Response(reply, address, port), addressBytes)

  private def readReply: Aux[Reply] =
    map1[Reply](Reply.apply)(ReplyEof)

  private def encodeMethods(methods: List[Method]): ByteVector =
    ByteVector(socks5.code, methods.length.toByte) ++ ByteVector(methods.map(_.code))

  private def encodeUserPassword(userPassword: UserPassword)(using Charset): Either[Error, ByteVector] =
    for
      userBytes <- encodeSizedString(userPassword.user.value)
      passwordBytes <- encodeSizedString(userPassword.password.value)
    yield
      version1.code +: (userBytes ++ passwordBytes)

  private def encodeRequest(request: Request)(using Charset): Either[Error, ByteVector] =
    encodeAddress(request.address).map { addressBytes =>
      val addressType = request.address match
        case ipv4: Ipv4Address => AddressType.Ipv4Address
        case ipv6: Ipv6Address => AddressType.Ipv6Address
        case _ => AddressType.DomainName
      socks5.code +: request.command.code +: Reserved.code +: addressType.code +: (addressBytes ++ encodePort(request.port))
    }
end ClientPullStateDsl
object ClientPullStateDsl:
  private class ClientPullStateDsl[F[_]] extends com.peknight.socks5.client.state.ClientPullStateDsl[F]
  def apply[F[_]]: com.peknight.socks5.client.state.ClientPullStateDsl[F] = new ClientPullStateDsl[F]
end ClientPullStateDsl
