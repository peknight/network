package com.peknight.socks5.client.state

import cats.ApplicativeError
import cats.effect.{Concurrent, Resource}
import cats.syntax.applicativeError.*
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
import fs2.{Pipe, Stream}
import scodec.bits.ByteVector

import java.nio.charset.Charset

object ClientPullState extends PullStateDsl:

  def apply[F[_], Auth, ConnectState, BindState, UDPAssociateState]
           (api: ClientApi[F, Auth, ConnectState, BindState, UDPAssociateState])
           (using Charset)(using Concurrent[F]): Aux[F, State] =
    val pullState: Aux[F, State] =
      for
        _ <- negotiation[F, Auth](api.negotiationApi.negotiation)(api.negotiationApi.noAuthenticationRequired)
        _ <- authentication[F, Auth](api.usernamePasswordApi.usernamePassword)(api.usernamePasswordApi.authenticated)(
          api.gssApiApi.gssApi, api.ianaAssignedApi.ianaAssigned, api.privateMethodApi.privateMethod)
        _ <- request[F, Auth, ConnectState, BindState, UDPAssociateState](api.requestApi.request)(
          api.connectApi.connect)(api.bindApi.bind)(api.udpAssociateApi.udpAssociate)
        _ <- established[F, Auth, ConnectState, BindState, UDPAssociateState](api.connectApi.tunnel)(api.bindApi.bound,
          api.udpAssociateApi.udpAssociated)
        state <- getS[F]
      yield
        state
    pullState.attempt

  def unsupportedCommand[F[_], Auth, S](state: Requested[Auth], response: Response)
                                       (using ApplicativeError[F, Throwable]): F[S] =
    com.peknight.socks5.error.UnsupportedCommand(state.request.command.code).raiseError[F, S]

  private def negotiation[F[_], Auth](f: Initial => F[List[Method]])(noAuthenticationRequired: Negotiating => F[Auth])
  : Aux[F, State] =
    for
      initial <- typedS[F, Initial]
      methods <- liftF(f(initial)).attempt
      negotiating = initial.negotiating(methods)
      _ <- setS(negotiating)
      _ <- output(encodeMethods(methods))
      selected <- readNegotiation[F]
      state <- selected match
        case NoAcceptableMethod => pure(negotiating.noAcceptableMethod)
        case NoAuthenticationRequired =>
          liftF(noAuthenticationRequired(negotiating)).attempt
            .map(negotiating.noAuthenticationRequired)
        case authRequiredMethod: AuthRequiredMethod =>
          pure(negotiating.authRequiredMethod(authRequiredMethod))
      _ <- setS(state)
    yield
      state

  private def authentication[F[_], Auth](f: AuthRequiredMethodSelected => F[UserPassword])
                                        (authenticated: UsernamePasswordAuthenticating => F[Auth])
                                        (gssApi: Aux[F, Auth], ianaAssigned: Aux[F, Auth], privateMethod: Aux[F, Auth])
                                        (using Charset): Aux[F, State] =
    getS[F].flatMap {
      case AuthRequiredMethodSelected(GSSAPI, _, _) => gssApi.flatMap(_ => getS[F])
      case AuthRequiredMethodSelected(UsernamePassword, _, _) =>
        passwordAuth[F, Auth](f)(authenticated)
      case AuthRequiredMethodSelected(IANAAssigned(_), _, _) => ianaAssigned.flatMap(_ => getS[F])
      case AuthRequiredMethodSelected(PrivateMethod(_), _, _) => privateMethod.flatMap(_ => getS[F])
      case state => pure(state)
    }

  private def passwordAuth[F[_], Auth](f: AuthRequiredMethodSelected => F[UserPassword])
                                      (authenticated: UsernamePasswordAuthenticating => F[Auth])(using Charset): Aux[F, State] =
    for
      authRequiredMethodSelected <- typedS[F, AuthRequiredMethodSelected]
      _ <- typed[F, UsernamePassword.type](authRequiredMethodSelected.selected)
      userPassword <- liftF(f(authRequiredMethodSelected)).attempt
      usernamePasswordAuthenticating = authRequiredMethodSelected.passwordUnsafe(userPassword)
      _ <- setS(usernamePasswordAuthenticating)
      bytes <- liftET(encodeUserPassword(userPassword))
      _ <- output(bytes)
      status <- readStatus[F]
      state <- status match
        case Success =>
          liftF(authenticated(usernamePasswordAuthenticating)).attempt
            .map(usernamePasswordAuthenticating.authenticated)
        case failure@Failure(_) => pure(usernamePasswordAuthenticating.failed(failure))
      _ <- setS(state)
    yield
      state

  private def request[F[_], Auth, ConnectState, BindState, UDPAssociateState]
                     (f: Authenticated[Auth] => F[Request])
                     (connectF: (Requested[Auth], Response) => F[ConnectState])
                     (bindF: (Requested[Auth], Response) => F[BindState])
                     (udpAssociateF: (Requested[Auth], Response) => F[UDPAssociateState])
                     (using Charset): Aux[F, State] =
    for
      authenticated <- typedS[F, Authenticated[Auth]]
      request <- liftF(f(authenticated)).attempt
      requested = authenticated.requested(request)
      _ <- setS(requested)
      bytes <- liftET(encodeRequest(request))
      _ <- output(bytes)
      (response, addressBytes) <- readResponse[F]
      state <- request.command match
        case CONNECT => handleRequest[F, Auth, ConnectState, Connected[Auth, ConnectState], ConnectFailed[Auth, ConnectState]](response)(connectF)(_.connected(_, _, addressBytes))(_.connectFailed(_, _, addressBytes))
        case BIND => handleRequest[F, Auth, BindState, Bound[Auth, BindState], BindFailed[Auth, BindState]](response)(bindF)(_.bound(_, _, addressBytes))(_.bindFailed(_, _, addressBytes))
        case UDP_ASSOCIATE => handleRequest[F, Auth, UDPAssociateState, UDPAssociated[Auth, UDPAssociateState], UDPAssociateFailed[Auth, UDPAssociateState]](response)(udpAssociateF)(_.udpAssociated(_, _, addressBytes))(_.udpAssociateFailed(_, _, addressBytes))
    yield
      state

  private def handleRequest[F[_], Auth, S, Success <: RespondedSuccessState[Auth, S], Failed <: RespondedFailedState[Auth, S]]
                           (response: Response)
                           (f: (Requested[Auth], Response) => F[S])
                           (success: (Requested[Auth], Response, S) => Success)
                           (failed: (Requested[Auth], Response, S) => Failed)
                           (using Charset): Aux[F, State] =
    for
      requested <- typedS[F, Requested[Auth]]
      s <- liftF[F, S](f(requested, response)).attempt
      state =
        if response.reply.success then success(requested, response, s)
        else failed(requested, response, s)
      _ <- setS(state)
    yield
      state

  private def established[F[_] : Concurrent, Auth, ConnectState, BindState, UDPAssociateState]
                         (tunnel: Connected[Auth, ConnectState] => Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])])
                         (bound: Aux[F, Unit], udpAssociated: Aux[F, Unit])
  : Aux[F, Unit] =
    getS[F].flatMap {
      case _: Connected[?, ?] => connected(tunnel)
      case _: Bound[?, ?] => bound
      case _: UDPAssociated[?, ?] => udpAssociated
      case state => liftT[F, Unit](WrongClassTag[RespondedSuccessState[?, ?]](state))
    }

  private def connected[F[_]: Concurrent, Auth, ConnectState](
    tunnel: Connected[Auth, ConnectState] => Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]
  ): Aux[F, Unit] =
    for
      connected <- typedS[F, Connected[Auth, ConnectState]]
      _ <- pipe[F](output => Stream
        .resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])](tunnel(connected))
        .flatMap((publish, input) => input.merge(output.through(publish).drain))
      ).attempt
      _ <- setS(connected.closed)
    yield
      ()

  private def readNegotiation[F[_]]: Aux[F, Method] =
    for
      _ <- readSocks5Version[F]
      method <- readMethod[F]
    yield
      method

  private def readMethod[F[_]]: Aux[F, Method] =
    map1[F, Method](Method.apply)(MethodEof)

  private def readStatus[F[_]]: Aux[F, Status] =
    for
      _ <- readPasswordVersion[F]
      status <- map1[F, Status](Status.apply)(StatusEof)
    yield
      status

  private def readResponse[F[_]](using Charset): Aux[F, (Response, ByteVector)] =
    for
      _ <- readSocks5Version[F]
      reply <- readReply[F]
      _ <- readReserved[F]
      (address, addressBytes) <- readAddress[F]
      port <- readPort[F]
    yield
      (Response(reply, address, port), addressBytes)

  private def readReply[F[_]]: Aux[F, Reply] =
    map1[F, Reply](Reply.apply)(ReplyEof)

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
end ClientPullState
