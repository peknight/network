package com.peknight.socks5.server.state

import cats.Applicative
import cats.effect.{Concurrent, Resource}
import cats.syntax.applicative.*
import cats.syntax.either.*
import com.comcast.ip4s.*
import com.peknight.auth.{Password, User, UserPassword as UPassword}
import com.peknight.cats.instances.eitherT.given
import com.peknight.error.Error
import com.peknight.error.std.WrongClassTag
import com.peknight.error.syntax.either.value
import com.peknight.socks.SocksVersion
import com.peknight.socks.SocksVersion.socks5
import com.peknight.socks.server.error.UnsupportedSocksVersion
import com.peknight.socks5.*
import com.peknight.socks5.Command.{BIND, CONNECT, UDP_ASSOCIATE}
import com.peknight.socks5.auth.Method
import com.peknight.socks5.auth.Method.*
import com.peknight.socks5.auth.password.PasswordVersion.version1
import com.peknight.socks5.auth.password.Status
import com.peknight.socks5.auth.password.Status.{Failure, Success}
import com.peknight.socks5.server.api.Socks5ServerApi
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
           (api: Socks5ServerApi[F, Auth, ConnectState, BindState, UDPAssociateState])
           (using Charset)(using Concurrent[F]): AUX[F, State] =
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

  def unsupportedMethod[F[_], A]: AUX[F, A] =
    for
      state <- typedS[F, AuthRequiredMethodSelected]
      a <- liftL[F, A](state.unsupportedMethod)
    yield
      a

  def unsupportedCommand[F[_], A]: AUX[F, A] =
    for
      state <- typedS[F, Requested[?]]
      a <- liftL[F, A](state.unsupportedCommand)
    yield
      a

  def unsupportedCommand[F[_] : Applicative, Auth, S](state: Requested[Auth], s: S): F[(Response, S)] =
    (Response.unsupportedCommand, s).pure[F]

  private def negotiation[F[_], Auth](f: Negotiating => F[Either[NoAcceptableMethod.type | AuthRequiredMethod, Auth]])
  : AUX[F, State] =
    val pullState: AUX[F, State] =
      for
        initial <- typedS[F, Initial]
        methods <- readNegotiation[F]
        negotiating = initial.negotiating(methods)
        _ <- setS(negotiating)
        either <- liftF[F, Either[NoAcceptableMethod.type | AuthRequiredMethod, Auth]](f(negotiating)).attempt
        state = either match
          case Left(NoAcceptableMethod) => negotiating.noAcceptableMethod
          case Left(selected: AuthRequiredMethod) => negotiating.authRequiredMethod(selected)
          case Right(auth) => negotiating.noAuthenticationRequired(auth)
        _ <- setS(state)
      yield
        state
    pullState.outputS(writeSelected)

  private def authentication[F[_], Auth](f: UsernamePasswordAuthenticating => F[Either[Failure, Auth]])
                                        (gssApi: AUX[F, Auth], ianaAssigned: AUX[F, Auth], privateMethod: AUX[F, Auth])
                                        (using Charset): AUX[F, State] =
    getS[F].flatMap {
      case state@AuthRequiredMethodSelected(GSSAPI, _, _) => gssApi.flatMap(_ => getS[F])
      case state@AuthRequiredMethodSelected(UsernamePassword, _, _) => passwordAuth[F, Auth](f)
      case state@AuthRequiredMethodSelected(IANAAssigned(_), _, _) => ianaAssigned.flatMap(_ => getS[F])
      case state@AuthRequiredMethodSelected(PrivateMethod(_), _, _) => privateMethod.flatMap(_ => getS[F])
      case state => pure(state)
    }

  private def passwordAuth[F[_], Auth](f: UsernamePasswordAuthenticating => F[Either[Failure, Auth]])(using Charset)
  : AUX[F, State] =
    val pullState: AUX[F, State] =
      for
        authRequiredMethodSelected <- typedS[F, AuthRequiredMethodSelected]
        _ <- typed[F, UsernamePassword.type](authRequiredMethodSelected.selected)
        usernamePassword <- readPasswordAuth[F]
        usernamePasswordAuthenticating = authRequiredMethodSelected.passwordUnsafe(usernamePassword)
        _ <- setS(usernamePasswordAuthenticating)
        either <- liftF[F, Either[Failure, Auth]](f(usernamePasswordAuthenticating)).attempt
        state = either match
          case Right(auth) => usernamePasswordAuthenticating.authenticated(auth)
          case Left(failure) => usernamePasswordAuthenticating.failed(failure)
        _ <- setS(state)
      yield
        state
    pullState.outputS(writeStatus)

  private def request[F[_], Auth, ConnectState, BindState, UDPAssociateState]
                     (connectF: Requested[Auth] => F[(Response, ConnectState)])
                     (bindF: Requested[Auth] => F[(Response, BindState)])
                     (udpAssociateF: Requested[Auth] => F[(Response, UDPAssociateState)])
                     (using Charset): AUX[F, State] =
    val pullState: AUX[F, State] =
      for
        authenticated <- typedS[F, Authenticated[Auth]]
        request <- readRequest[F]
        requested = authenticated.requested(request)
        _ <- setS(requested)
        state <- request.command match
          case CONNECT => handleRequest[F, Auth, ConnectState, Connected[Auth, ConnectState], ConnectFailed[Auth, ConnectState]](connectF)(_.connected(_, _, _))(_.connectFailed(_, _, _))
          case BIND => handleRequest[F, Auth, BindState, Bound[Auth, BindState], BindFailed[Auth, BindState]](bindF)(_.bound(_, _, _))(_.bindFailed(_, _, _))
          case UDP_ASSOCIATE => handleRequest[F, Auth, UDPAssociateState, UDPAssociated[Auth, UDPAssociateState], UDPAssociateFailed[Auth, UDPAssociateState]](udpAssociateF)(_.udpAssociated(_, _, _))(_.udpAssociateFailed(_, _, _))
      yield
        state
    pullState.outputS(writeResponse)

  private def handleRequest[F[_], Auth, S, Success <: RespondedSuccessState[Auth, S], Failed <: RespondedFailedState[Auth, S]]
                           (f: Requested[Auth] => F[(Response, S)])
                           (success: (Requested[Auth], Response, S, ByteVector) => Success)
                           (failed: (Requested[Auth], Response, S, ByteVector) => Failed)
                           (using Charset): AUX[F, State] =
    for
      requested <- typedS[F, Requested[Auth]]
      (response, s) <- liftF[F, (Response, S)](f(requested)).attempt
      addressBytes <- liftET[F, ByteVector](writeAddress(response.address))
      state =
        if response.reply.success then success(requested, response, s, addressBytes)
        else failed(requested, response, s, addressBytes)
      _ <- setS(state)
    yield
      state

  private def established[F[_] : Concurrent, Auth, ConnectState, BindState, UDPAssociateState]
                         (tunnel: Connected[Auth, ConnectState] => Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])])
                         (bound: AUX[F, Unit], udpAssociated: AUX[F, Unit])
  : AUX[F, Unit] =
    getS[F].flatMap {
      case _: Connected[?, ?] => connected(tunnel)
      case _: Bound[?, ?] => bound
      case _: UDPAssociated[?, ?] => udpAssociated
      case state => liftT[F, Unit](WrongClassTag[RespondedSuccessState[?, ?]](state))
    }

  private def connected[F[_] : Concurrent, Auth, ConnectState](
                                                                tunnel: Connected[Auth, ConnectState] => Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]
                                                              ): AUX[F, Unit] =
    for
      connected <- typedS[F, Connected[Auth, ConnectState]]
      _ <- pipe[F](in => Stream
        .resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])](tunnel(connected))
        .flatMap((send, receive) => receive.mergeHaltBoth(in.through(send).drain))
      ).attempt
      _ <- setS(connected.closed)
    yield
      ()

  private def readNegotiation[F[_]]: AUX[F, List[Method]] =
    for
      _ <- readSocks5Version[F]
      methods <- readMethods[F]
    yield
      methods

  private def readSocks5Version[F[_]]: AUX[F, SocksVersion] =
    parse1[F, SocksVersion](version =>
      if version == socks5.code then socks5.asRight
      else UnsupportedSocksVersion(version).asLeft
    )(Socks5VersionEof)

  private def readMethods[F[_]]: AUX[F, List[Method]] =
    mapSizedBytes[F, List[Method]](_.map(Method.apply).toList)(MethodEof)

  private def readPasswordAuth[F[_]](using Charset): AUX[F, UPassword] =
    for
      _ <- readPasswordVersion[F]
      username <- readSizedString[F](UsernameEof)
      password <- readSizedString[F](PasswordEof)
    yield
      UPassword(User(username), Password(password))

  private def readPasswordVersion[F[_]]: AUX[F, Unit] =
    parse1[F, Unit](version =>
      if version == version1.code then ().asRight
      else UnsupportedPasswordVersion(version).asLeft
    )(PasswordVersionEof)

  private def readRequest[F[_]](using Charset): AUX[F, Request] =
    for
      _ <- readSocks5Version[F]
      command <- readCommand[F]
      _ <- readReserved[F]
      address <- readAddress[F]
      port <- readPort[F]
    yield
      Request(command, address, port)

  private def readCommand[F[_]]: AUX[F, Command] =
    parse1[F, Command](cmd =>
      Command.values.find(_.code == cmd).toRight(UnsupportedCommand(cmd))
    )(CommandEof)

  private def readReserved[F[_]]: AUX[F, Unit] =
    parse1[F, Unit](rsv =>
      if rsv == Reserved.code then ().asRight
      else UnsupportedReserved(rsv).asLeft
    )(ReservedEof)

  private def readAddress[F[_]](using Charset): AUX[F, Host] =
    for
      addressType <- readAddressType[F]
      address <- addressType match
        case AddressType.Ipv4Address => readIpv4Address[F]
        case AddressType.DomainName => readDomainName[F]
        case AddressType.Ipv6Address => readIpv6Address[F]
    yield
      address

  private def readAddressType[F[_]]: AUX[F, AddressType] =
    parse1[F, AddressType](code =>
      AddressType.values.find(_.code == code).toRight(UnsupportedAddressType(code))
    )(AddressTypeEof)

  private def readIpv4Address[F[_]]: AUX[F, Ipv4Address] =
    parseChunk[F, Ipv4Address](_.unconsN(4))(chunk =>
      Ipv4Address.fromBytes(chunk.toArray).toRight(IllegalIpv4Address(chunk.toByteVector))
    )(Ipv4AddressEof)

  private def readDomainName[F[_]](using Charset): AUX[F, Hostname] =
    parseSizedString[F, Hostname](domainName =>
      Hostname.fromString(domainName).toRight(IllegalDomainName(domainName))
    )(DomainNameEof)

  private def readIpv6Address[F[_]]: AUX[F, Ipv6Address] =
    parseChunk[F, Ipv6Address](_.unconsN(16))(chunk =>
      Ipv6Address.fromBytes(chunk.toArray).toRight(IllegalIpv6Address(chunk.toByteVector))
    )(Ipv6AddressEof)

  private def readPort[F[_]]: AUX[F, Port] =
    parseChunk[F, Port](_.unconsN(2)) { chunk =>
      val port = chunk.toByteVector.toInt()
      Port.fromInt(port).toRight(IllegalPort(port))
    }(PortEof)

  private def writeSelected(state: State): ByteVector =
    state match
      case s: MethodSelectedState[Method] => ByteVector(socks5.code, s.selected.code)
      case s => ByteVector(socks5.code, NoAcceptableMethod.code)

  private def writeStatus(state: State): ByteVector =
    val status: Status = state match
      case _: Authenticated[?] => Success
      case UsernamePasswordFailed(status, _, _) => status
      case _ => Failure.default
    ByteVector(version1.code, status.code)

  private def writeResponse(state: State): ByteVector =
    val (response, addressBytes) = state match
      case s: RespondedState[?, ?] => (s.response, s.addressBytes)
      case _ => (toResponse(state), writeIpAddress(Response.defaultHost))
    val addressTypeCode = AddressType.fromHost(response.address).code
    ByteVector(socks5.code, response.reply.code, Reserved.code, addressTypeCode) ++
      addressBytes ++
      writePort(response.port)

  private def writeAddress(host: Host)(using Charset): Either[Error, ByteVector] =
    host match
      case ipAddress: IpAddress => writeIpAddress(ipAddress).asRight
      case host => writeHost(host)

  private def writeIpAddress(ipAddress: IpAddress): ByteVector = ByteVector(ipAddress.toBytes)

  private def writeHost(host: Host)(using Charset): Either[Error, ByteVector] =
    ByteVector.encodeString(host.toString).value(host).map(bytes => bytes.length.toByte +: bytes)

  private def writePort(port: Port): ByteVector = ByteVector.fromInt(port.value, 2)
end ServerPullState
