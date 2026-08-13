package com.peknight.socks5.server

import cats.syntax.either.*
import cats.syntax.functor.*
import com.comcast.ip4s.*
import com.peknight.error.Error
import com.peknight.error.syntax.either.value
import com.peknight.fs2.pull.state.BytePullState
import com.peknight.fs2.pull.state.BytePullState.attempt
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
import fs2.{Chunk, Pull, RaiseThrowable}
import scodec.bits.ByteVector

import java.nio.charset.Charset

package object state:
  type State[F[_], A] = BytePullState[F, Byte, A]

  private def negotiation[F[_]: RaiseThrowable](f: List[Method] => F[Method]): State[F, AcceptableMethod] =
    val state: State[F, AcceptableMethod] =
      for
        _ <- readSocks5Version[F]
        methods <- readMethods[F]
        selected <- BytePullState.liftP[F, Byte, AcceptableMethod](Pull.eval(f(methods)).flatMap {
          case NoAcceptableMethod => Pull.raiseError(NoAcceptableMethod)
          case selected: AcceptableMethod => Pull.pure(selected)
        })
      yield
        selected
    state.attempt.flatMap {
      case Right(selected) =>
        BytePullState.output(socks5.code, selected.code).as(selected)
      case Left(error) =>
        BytePullState.output(socks5.code, NoAcceptableMethod.code).flatMap(_ => BytePullState.raiseError(error))
    }

  private def authentication[F[_]](method: AcceptableMethod)(password: UPassword => F[Status])
                                  (using Charset)(using RaiseThrowable[F]): State[F, Unit] =
    method match
      case NoAuthenticationRequired => BytePullState.unit
      case GSSAPI => BytePullState.raiseError(UnsupportedMethod(method))
      case UsernamePassword => passwordAuth(password)
      case method@IANAAssigned(code) => BytePullState.raiseError(UnsupportedMethod(method))
      case method@PrivateMethod(code) => BytePullState.raiseError(UnsupportedMethod(method))

  private def passwordAuth[F[_], O](f: UPassword => F[Status])(using Charset)(using RaiseThrowable[F]): State[F, Unit] =
    val state: State[F, Unit] =
      for
        _ <- readPasswordVersion[F]
        username <- BytePullState.readSizedString[F, Byte](UsernameEmpty)
        password <- BytePullState.readSizedString[F, Byte](PasswordEmpty)
        _ <- BytePullState.liftP[F, Byte, Unit](Pull.eval(f(UPassword(username, password))).flatMap {
          case Success => Pull.pure(())
          case f@Failure(code) => Pull.raiseError(f)
        })
      yield
        ()
    state.attempt.flatMap {
      case Right(_) => BytePullState.output(version1.code, Success.code)
      case Left(f@Failure(code)) => BytePullState.output(version1.code, code).flatMap(_ => BytePullState.raiseError(f))
      case Left(e@error) => BytePullState.output(version1.code, Failure.code).flatMap(_ => BytePullState.raiseError(e))
    }

  private def request[F[_]](f: Request => F[Response])(using Charset)(using RaiseThrowable[F]): State[F, Response] =
    val state: State[F, (Response, Chunk[Byte])] =
      for
        _ <- readSocks5Version[F]
        command <- readCommand[F]
        _ <- readReserved[F]
        address <- readAddress[F]
        port <- readPort[F]
        response <- BytePullState.liftF[F, Byte, Response](f(Request(command, address, port)))
        addressBytes <- BytePullState.liftE[F, Byte, Chunk[Byte]](encodeAddress(response.address))
      yield
        (response, addressBytes)
    state.attempt.flatMap {
      case Right((response, addressBytes)) =>
        val addressTypeCode = AddressType.fromHost(response.address).code
        for
          _ <- BytePullState.output(socks5.code, response.reply.code, Reserved.code, addressTypeCode)
          _ <- BytePullState.output(addressBytes)
          _ <- BytePullState.output(encodePort(response.port))
        yield
          response
      case Left(error) =>
        for
          _ <- BytePullState.output(socks5.code, Reply.fromError(error).code, Reserved.code, AddressType.Ipv4Address.code)
          _ <- BytePullState.output(encodeIpAddress(ipv4"0.0.0.0"))
          _ <- BytePullState.output(encodePort(port"0"))
          response <- BytePullState.raiseError[F, Byte, Response](error)
        yield
          response
    }

  private def readSocks5Version[F[_]: RaiseThrowable]: State[F, SocksVersion] =
    BytePullState.parse1[F, Byte, SocksVersion](version =>
      if version == socks5.code then socks5.asRight
      else UnsupportedSocksVersion(version).asLeft
    )(Socks5VersionEmpty)

  private def readMethods[F[_]: RaiseThrowable]: State[F, List[Method]] =
    BytePullState.mapSizedBytes[F, Byte, List[Method]](_.map(Method.apply).toList)(MethodEmpty)

  private def readPasswordVersion[F[_]: RaiseThrowable]: State[F, Unit] =
    BytePullState.parse1[F, Byte, Unit](version =>
      if version == version1.code then ().asRight
      else UnsupportedPasswordVersion(version).asLeft
    )(PasswordVersionEmpty)

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

  private def encodeAddress(host: Host)(using Charset): Either[Error, Chunk[Byte]] =
    host match
      case ipAddress: IpAddress => encodeIpAddress(ipAddress).asRight
      case host => encodeHost(host)

  private def encodeIpAddress(ipAddress: IpAddress): Chunk[Byte] = Chunk.array(ipAddress.toBytes)

  private def encodeHost(host: Host)(using Charset): Either[Error, Chunk[Byte]] =
    ByteVector.encodeString(host.toString).value(host).map(bytes => Chunk.byteVector(bytes.length.toByte +: bytes))

  private def encodePort(port: Port): Chunk[Byte] = Chunk.byteVector(ByteVector.fromInt(port.value, 2))

end state
