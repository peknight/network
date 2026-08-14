package com.peknight.socks5.server

import cats.syntax.either.*
import cats.syntax.functor.*
import com.comcast.ip4s.*
import com.peknight.error.Error
import com.peknight.error.syntax.either.value
import com.peknight.fs2.pull.state.BytePullState
import com.peknight.fs2.pull.state.BytePullState.outputBytesE
import com.peknight.socks.SocksVersion
import com.peknight.socks.SocksVersion.socks5
import com.peknight.socks.error.UnsupportedSocksVersion
import com.peknight.socks5.*
import com.peknight.socks5.auth.Method
import com.peknight.socks5.auth.Method.*
import com.peknight.socks5.auth.password.PasswordVersion.version1
import com.peknight.socks5.auth.password.Status.{Failure, Success}
import com.peknight.socks5.auth.password.{Status, UsernamePassword as UPassword}
import com.peknight.socks5.error.*
import fs2.{Pull, RaiseThrowable}
import scodec.bits.ByteVector

import java.nio.charset.Charset

package object state:
  type State[F[_], A] = BytePullState[F, Byte, A]

  private def negotiation[F[_]: RaiseThrowable](f: List[Method] => F[Method]): State[F, AcceptableMethod] =
    readNegotiation[F]
      .flatMap(methods => BytePullState.liftF[F, Byte, Method](f(methods)))
      .flatMap {
        case NoAcceptableMethod => BytePullState.raiseError[F, Byte, AcceptableMethod](NoAcceptableMethod)
        case selected: AcceptableMethod => BytePullState.pure(selected)
      }
      .outputBytesE(either => writeNegotiation(either.toOption))

  private def authentication[F[_]](method: AcceptableMethod)(password: UPassword => F[Status])
                                  (using Charset)(using RaiseThrowable[F]): State[F, Unit] =
    method match
      case NoAuthenticationRequired => BytePullState.unit
      case GSSAPI => BytePullState.raiseError(UnsupportedMethod(method))
      case UsernamePassword => passwordAuth(password).as(())
      case method@IANAAssigned(code) => BytePullState.raiseError(UnsupportedMethod(method))
      case method@PrivateMethod(code) => BytePullState.raiseError(UnsupportedMethod(method))

  private def passwordAuth[F[_]](f: UPassword => F[Status])(using Charset)(using RaiseThrowable[F]): State[F, Status] =
    readPasswordAuth[F]
      .flatMap(userPassword => BytePullState.liftF[F, Byte, Status](f(userPassword)))
      .flatMap {
        case s@Success => BytePullState.pure[F, Byte, Status](s)
        case f@Failure(code) => BytePullState.raiseError(f)
      }
      .outputBytesE(writePasswordAuth)

  private def request[F[_]](f: Request => F[Response])(using Charset)(using RaiseThrowable[F]): State[F, Response] =
    val state: State[F, (Response, ByteVector)] =
      for
        request <- readRequest[F]
        response <- BytePullState.liftF[F, Byte, Response](f(request))
        addressBytes <- BytePullState.liftE[F, Byte, ByteVector](writeAddress(response.address))
      yield
        (response, addressBytes)
    state.outputBytesE(writeRequest).map(_._1)

  private def readNegotiation[F[_]: RaiseThrowable]: State[F, List[Method]] =
    for
      _ <- readSocks5Version[F]
      methods <- readMethods[F]
    yield
      methods

  private def readSocks5Version[F[_]: RaiseThrowable]: State[F, SocksVersion] =
    BytePullState.parse1[F, Byte, SocksVersion](version =>
      if version == socks5.code then socks5.asRight
      else UnsupportedSocksVersion(version).asLeft
    )(Socks5VersionEmpty)

  private def readMethods[F[_]: RaiseThrowable]: State[F, List[Method]] =
    BytePullState.mapSizedBytes[F, Byte, List[Method]](_.map(Method.apply).toList)(MethodEmpty)

  private def readPasswordAuth[F[_]](using Charset)(using RaiseThrowable[F]): State[F, UPassword] =
    for
      _ <- readPasswordVersion[F]
      username <- BytePullState.readSizedString[F, Byte](UsernameEmpty)
      password <- BytePullState.readSizedString[F, Byte](PasswordEmpty)
    yield
      UPassword(username, password)

  private def readPasswordVersion[F[_]: RaiseThrowable]: State[F, Unit] =
    BytePullState.parse1[F, Byte, Unit](version =>
      if version == version1.code then ().asRight
      else UnsupportedPasswordVersion(version).asLeft
    )(PasswordVersionEmpty)

  private def readRequest[F[_]](using Charset)(using RaiseThrowable[F]): State[F, Request] =
    for
      _ <- readSocks5Version[F]
      command <- readCommand[F]
      _ <- readReserved[F]
      address <- readAddress[F]
      port <- readPort[F]
    yield
      Request(command, address, port)

  private def readCommand[F[_]: RaiseThrowable]: State[F, Command] =
    BytePullState.parse1[F, Byte, Command](cmd =>
      Command.values.find(_.code == cmd).toRight(UnsupportedCommand(cmd))
    )(CommandEmpty)

  private def readReserved[F[_]: RaiseThrowable]: State[F, Unit] =
    BytePullState.parse1[F, Byte, Unit](rsv =>
      if rsv == Reserved.code then ().asRight
      else UnsupportedReserved(rsv).asLeft
    )(ReservedEmpty)

  private def readAddress[F[_]](using Charset)(using RaiseThrowable[F]): State[F, Host] =
    for
      addressType <- readAddressType[F]
      address <- addressType match
        case AddressType.Ipv4Address => readIpv4Address[F]
        case AddressType.DomainName => readDomainName[F]
        case AddressType.Ipv6Address => readIpv6Address[F]
    yield
      address

  private def readAddressType[F[_]: RaiseThrowable]: State[F, AddressType] =
    BytePullState.parse1[F, Byte, AddressType](code =>
      AddressType.values.find(_.code == code).toRight(UnsupportedAddressType(code))
    )(AddressTypeEmpty)

  private def readIpv4Address[F[_]: RaiseThrowable]: State[F, Ipv4Address] =
    BytePullState.parseChunk[F, Byte, Ipv4Address](_.unconsN(4))(chunk =>
      Ipv4Address.fromBytes(chunk.toArray).toRight(IllegalIpv4Address(chunk.toByteVector))
    )(Ipv4AddressEmpty)

  private def readDomainName[F[_]](using Charset)(using RaiseThrowable[F]): State[F, Hostname] =
    BytePullState.parseSizedString[F, Byte, Hostname](domainName =>
      Hostname.fromString(domainName).toRight(IllegalDomainName(domainName))
    )(DomainNameEmpty)

  private def readIpv6Address[F[_]: RaiseThrowable]: State[F, Ipv6Address] =
    BytePullState.parseChunk[F, Byte, Ipv6Address](_.unconsN(16))(chunk =>
      Ipv6Address.fromBytes(chunk.toArray).toRight(IllegalIpv6Address(chunk.toByteVector))
    )(Ipv6AddressEmpty)

  private def readPort[F[_]: RaiseThrowable]: State[F, Port] =
    BytePullState.parseChunk[F, Byte, Port](_.unconsN(2)) { chunk =>
      val port = chunk.toByteVector.toInt()
      Port.fromInt(port).toRight(IllegalPort(port))
    }(PortEmpty)

  private def writeNegotiation(selected: Option[AcceptableMethod]): ByteVector =
    selected match
      case Some(selected) => ByteVector(socks5.code, selected.code)
      case None => ByteVector(socks5.code, NoAcceptableMethod.code)

  private def writePasswordAuth(either: Either[Throwable, Status]): ByteVector =
    either match
      case Right(_) => ByteVector(version1.code, Success.code)
      case Left(f@Failure(code)) => ByteVector(version1.code, code)
      case Left(error) => ByteVector(version1.code, Failure.code)

  private def writeRequest(either: Either[Throwable, (Response, ByteVector)]): ByteVector =
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
