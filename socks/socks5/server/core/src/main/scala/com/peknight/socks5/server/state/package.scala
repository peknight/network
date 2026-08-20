package com.peknight.socks5.server

import cats.syntax.either.*
import cats.syntax.functor.*
import com.comcast.ip4s.*
import com.peknight.cats.instances.eitherT.given
import com.peknight.error.Error
import com.peknight.error.syntax.either.value
import com.peknight.socks.SocksVersion
import com.peknight.socks.SocksVersion.socks5
import com.peknight.socks.error.UnsupportedSocksVersion
import com.peknight.socks5.*
import com.peknight.socks5.Command.{BIND, CONNECT, UDP_ASSOCIATE}
import com.peknight.socks5.Socks5PullState.outputE
import com.peknight.socks5.State.Negotiating
import com.peknight.socks5.api.Socks5Api
import com.peknight.socks5.auth.Method
import com.peknight.socks5.auth.Method.*
import com.peknight.socks5.auth.password.PasswordVersion.version1
import com.peknight.socks5.auth.password.Status.{Failure, Success}
import com.peknight.socks5.auth.password.{Status, UsernamePassword as UPassword}
import com.peknight.socks5.error.*
import fs2.io.net.Socket
import fs2.{Pull, RaiseThrowable}
import scodec.bits.ByteVector

import java.nio.charset.Charset

package object state:

  def handle[F[_], Auth](api: Socks5Api[F, Auth], socket: Socket[F])(using Charset): F[Unit] =
    val connection = Connection(socket.address, socket.peerAddress)
    val state =
      for
        acceptableMethod <- negotiation[F](method => api.negotiation(method, connection))
        _ <- authentication[F](acceptableMethod)(password => api.passwordAuth(password, connection))
        response <- request[F](request => api.connect(request, connection))
      yield
        ???
    ???


  private def negotiation[F[_], Auth](f: Negotiating => F[Either[NoAcceptableMethod.type | AuthRequiredMethod, Auth]])
  : Socks5PullState[F, AcceptableMethod] = {
    readNegotiation[F]
      .flatMap(methods => Socks5PullState.liftF[F, Either[NoAcceptableMethod.type | AuthRequiredMethod, Auth]](f(methods)).attempt)
      .flatMap {
        case NoAcceptableMethod => Socks5PullState.raiseError[F, Byte, State, AcceptableMethod](NoAcceptableMethod)
        case selected: AcceptableMethod => Socks5PullState.pure(selected)
      }
      .outputBytesE(either => writeSelected(either.toOption))
  }

  def authentication[F[_]](method: AcceptableMethod)(password: UPassword => F[Status])(using Charset): Socks5PullState[F, Unit] =
    method match
      case NoAuthenticationRequired => Socks5PullState.unit
      case GSSAPI => Socks5PullState.raiseError(UnsupportedMethod(method))
      case UsernamePassword => passwordAuth(password).as(())
      case method@IANAAssigned(code) => Socks5PullState.raiseError(UnsupportedMethod(method))
      case method@PrivateMethod(code) => Socks5PullState.raiseError(UnsupportedMethod(method))

  private def passwordAuth[F[_]](f: UPassword => F[Status])(using Charset): Socks5PullState[F, Status] =
    readPasswordAuth[F]
      .flatMap(userPassword => Socks5PullState.liftF[F, Byte, State, Status](f(userPassword)))
      .flatMap {
        case s@Success => Socks5PullState.pure[F, Byte, State, Status](s)
        case f@Failure(code) => Socks5PullState.raiseError(f)
      }
      .outputBytesE(writeStatus)

  def request[F[_]](connect: Request => F[Response])(using Charset): Socks5PullState[F, Response] =
    val socks5PullState: Socks5PullState[F, (Response, ByteVector)] =
      for
        request <- readRequest[F]
        response <- request.command match
          case CONNECT =>
            Socks5PullState.liftF[F, Byte, State, Response](connect(request)).flatMap(response =>
              if response.reply.success then Socks5PullState.pure[F, Byte, State, Response](response)
              else Socks5PullState.raiseError(Error(response.reply)))
          case BIND => Socks5PullState.raiseError(UnsupportedCommand(request.command.code))
          case UDP_ASSOCIATE => Socks5PullState.raiseError(UnsupportedCommand(request.command.code))
        addressBytes <- Socks5PullState.liftE[F, Byte, State, ByteVector](writeAddress(response.address))
      yield
        (response, addressBytes)
    socks5PullState.outputBytesE(writeResponse).map(_._1)

  private def readNegotiation[F[_]]: Socks5PullState[F, List[Method]] =
    for
      _ <- readSocks5Version[F]
      methods <- readMethods[F]
    yield
      methods

  private def readSocks5Version[F[_]]: Socks5PullState[F, SocksVersion] =
    Socks5PullState.parse1[F, SocksVersion](version =>
      if version == socks5.code then socks5.asRight
      else UnsupportedSocksVersion(version).asLeft
    )(Socks5VersionEmpty)

  private def readMethods[F[_]]: Socks5PullState[F, List[Method]] =
    Socks5PullState.mapSizedBytes[F, List[Method]](_.map(Method.apply).toList)(MethodEmpty)

  private def readPasswordAuth[F[_]](using Charset): Socks5PullState[F, UPassword] =
    for
      _ <- readPasswordVersion[F]
      username <- Socks5PullState.readSizedString[F](UsernameEmpty)
      password <- Socks5PullState.readSizedString[F](PasswordEmpty)
    yield
      UPassword(username, password)

  private def readPasswordVersion[F[_]]: Socks5PullState[F, Unit] =
    Socks5PullState.parse1[F, Unit](version =>
      if version == version1.code then ().asRight
      else UnsupportedPasswordVersion(version).asLeft
    )(PasswordVersionEmpty)

  private def readRequest[F[_]](using Charset): Socks5PullState[F, Request] =
    for
      _ <- readSocks5Version[F]
      command <- readCommand[F]
      _ <- readReserved[F]
      address <- readAddress[F]
      port <- readPort[F]
    yield
      Request(command, address, port)

  private def readCommand[F[_]]: Socks5PullState[F, Command] =
    Socks5PullState.parse1[F, Command](cmd =>
      Command.values.find(_.code == cmd).toRight(UnsupportedCommand(cmd))
    )(CommandEmpty)

  private def readReserved[F[_]]: Socks5PullState[F, Unit] =
    Socks5PullState.parse1[F, Unit](rsv =>
      if rsv == Reserved.code then ().asRight
      else UnsupportedReserved(rsv).asLeft
    )(ReservedEmpty)

  private def readAddress[F[_]](using Charset): Socks5PullState[F, Host] =
    for
      addressType <- readAddressType[F]
      address <- addressType match
        case AddressType.Ipv4Address => readIpv4Address[F]
        case AddressType.DomainName => readDomainName[F]
        case AddressType.Ipv6Address => readIpv6Address[F]
    yield
      address

  private def readAddressType[F[_]]: Socks5PullState[F, AddressType] =
    Socks5PullState.parse1[F, AddressType](code =>
      AddressType.values.find(_.code == code).toRight(UnsupportedAddressType(code))
    )(AddressTypeEmpty)

  private def readIpv4Address[F[_]]: Socks5PullState[F, Ipv4Address] =
    Socks5PullState.parseChunk[F, Ipv4Address](_.unconsN(4))(chunk =>
      Ipv4Address.fromBytes(chunk.toArray).toRight(IllegalIpv4Address(chunk.toByteVector))
    )(Ipv4AddressEmpty)

  private def readDomainName[F[_]](using Charset): Socks5PullState[F, Hostname] =
    Socks5PullState.parseSizedString[F, Hostname](domainName =>
      Hostname.fromString(domainName).toRight(IllegalDomainName(domainName))
    )(DomainNameEmpty)

  private def readIpv6Address[F[_]]: Socks5PullState[F, Ipv6Address] =
    Socks5PullState.parseChunk[F, Ipv6Address](_.unconsN(16))(chunk =>
      Ipv6Address.fromBytes(chunk.toArray).toRight(IllegalIpv6Address(chunk.toByteVector))
    )(Ipv6AddressEmpty)

  private def readPort[F[_]]: Socks5PullState[F, Port] =
    Socks5PullState.parseChunk[F, Port](_.unconsN(2)) { chunk =>
      val port = chunk.toByteVector.toInt()
      Port.fromInt(port).toRight(IllegalPort(port))
    }(PortEmpty)

  private def writeSelected(selected: Option[AcceptableMethod]): ByteVector =
    selected match
      case Some(selected) => ByteVector(socks5.code, selected.code)
      case None => ByteVector(socks5.code, NoAcceptableMethod.code)

  private def writeStatus(either: Either[Throwable, Status]): ByteVector =
    either match
      case Right(_) => ByteVector(version1.code, Success.code)
      case Left(f@Failure(code)) => ByteVector(version1.code, code)
      case Left(error) => ByteVector(version1.code, Failure.code)

  private def writeResponse(either: Either[Throwable, (Response, ByteVector)]): ByteVector =
    either match
      case Right((response, addressBytes)) =>
        val addressTypeCode = AddressType.fromHost(response.address).code
        ByteVector(socks5.code, response.reply.code, Reserved.code, addressTypeCode) ++
          addressBytes ++
          writePort(response.port)
      case Left(error) =>
        ByteVector(socks5.code, Reply.fromError(error).code, Reserved.code, AddressType.Ipv4Address.code) ++
          writeIpAddress(ipv4"0.0.0.0") ++
          writePort(port"0")

  private def writeAddress(host: Host)(using Charset): Either[Error, ByteVector] =
    host match
      case ipAddress: IpAddress => writeIpAddress(ipAddress).asRight
      case host => writeHost(host)

  private def writeIpAddress(ipAddress: IpAddress): ByteVector = ByteVector(ipAddress.toBytes)

  private def writeHost(host: Host)(using Charset): Either[Error, ByteVector] =
    ByteVector.encodeString(host.toString).value(host).map(bytes => bytes.length.toByte +: bytes)

  private def writePort(port: Port): ByteVector = ByteVector.fromInt(port.value, 2)

end state
