package com.peknight.socks5.server

import cats.Show
import cats.data.StateT
import cats.syntax.either.*
import com.comcast.ip4s.*
import com.peknight.cats.ext.instances.eitherT.eitherTMonad
import com.peknight.error.Error
import com.peknight.error.syntax.either.value
import com.peknight.socks.SocksVersion.socks5
import com.peknight.socks.error.UnsupportedSocksVersion
import com.peknight.socks5.*
import com.peknight.socks5.auth.Method
import com.peknight.socks5.auth.Method.*
import com.peknight.socks5.auth.password.PasswordVersion.version1
import com.peknight.socks5.auth.password.Status.{Failure, Success}
import com.peknight.socks5.auth.password.{Status, UsernamePassword as UPassword}
import com.peknight.socks5.error.*
import com.peknight.socks5.server.state.State
import fs2.io.net.Socket
import fs2.{Chunk, Pull, Stream}
import scodec.bits.ByteVector

import java.nio.charset.Charset

package object state:
  type State[F[_], O, A] = StateT[[X] =>> Pull[F, O, Either[Error, X]], Stream[F, Byte], A]

  private def negotiation[F[_]](socket: Socket[F])(f: (List[Method], Socket[F]) => F[Method])
  : State[F, Stream[F, Byte], AcceptableMethod] =
    val state: State[F, Nothing, AcceptableMethod] =
      for
        _ <- readSocks5Version[F, Nothing]
        chunk <- readSizedChunk[F, Nothing](MethodEmpty)
        methods = chunk.map(Method.apply).toList
        selected <- liftF[F, Nothing, AcceptableMethod](Pull.eval(f(methods, socket)).map {
          case NoAcceptableMethod => NoAcceptableMethod.asLeft
          case selected: AcceptableMethod => selected.asRight
        })
      yield
        selected
    State[F, Stream[F, Byte], AcceptableMethod](stream => state.run(stream).flatMap {
      case r@Right((stream, selected)) => Pull.output1(Stream(socks5.code, selected.code)) >> Pull.pure(r)
      case l@Left(error) => Pull.output1(Stream(socks5.code, NoAcceptableMethod.code)) >> Pull.pure(l)
    })

  private def authentication[F[_]](method: AcceptableMethod, socket: Socket[F])
                                  (password: (UPassword, Socket[F]) => F[Status])
                                  (using Charset): State[F, Stream[F, Byte], Unit] =
    method match
      case NoAuthenticationRequired => unit
      case GSSAPI => liftEither(UnsupportedMethod(method).asLeft)
      case UsernamePassword => passwordAuth(socket)(password)
      case method@IANAAssigned(code) => liftEither(UnsupportedMethod(method).asLeft)
      case method@PrivateMethod(code) => liftEither(UnsupportedMethod(method).asLeft)

  private def passwordAuth[F[_]](socket: Socket[F])(f: (UPassword, Socket[F]) => F[Status])(using Charset)
  : State[F, Stream[F, Byte], Unit] =
    val state: State[F, Nothing, Unit] =
      for
        _ <- readPasswordVersion[F, Nothing]
        username <- readSizedString[F, Nothing](UsernameEmpty)
        password <- readSizedString[F, Nothing](PasswordEmpty)
        _ <- liftF[F, Nothing, Unit](Pull.eval(f(UPassword(username, password), socket)).map {
          case Success => ().asRight
          case f@Failure(code) => f.asLeft
        })
      yield
        ()
    State[F, Stream[F, Byte], Unit](stream => state.run(stream).flatMap {
      case r@Right((stream, _)) => Pull.output1(Stream(version1.code, Success.code)) >> Pull.pure(r)
      case l@Left(Failure(code)) => Pull.output1(Stream(version1.code, code)) >> Pull.pure(l)
      case l@Left(error) => Pull.output1(Stream(version1.code, Failure.code)) >> Pull.pure(l)
    })

  private def request[F[_]](socket: Socket[F])(f: (Request, Socket[F]) => F[Response])(using Charset)
  : State[F, Stream[F, Byte], Response] =
    val state: State[F, Nothing, Response] =
      for
        _ <- readSocks5Version[F, Nothing]
        command <- readCommand[F, Nothing]
        _ <- readReserved[F, Nothing]
        address <- readAddress[F, Nothing]
        port <- readPort[F, Nothing]
        response <- liftF[F, Nothing, Response](Pull.eval(f(Request(command, address, port), socket)).map(_.asRight))
      yield
        response
    State[F, Stream[F, Byte], Response](stream => state.run(stream).flatMap { either =>
      val next =
        for
          (stream, response) <- either
          addressBytes <- writeAddress[F](response.address)
        yield
          (stream, response, addressBytes)
      next match
        case Right((stream, response, addressBytes)) =>
          val addressTypeCode = AddressType.fromHost(response.address).code
          Pull.output1(Stream(socks5.code, response.reply.code, Reserved.code, addressTypeCode)
            .append(addressBytes).append(writePort(response.port))) >>
            Pull.pure((stream, response).asRight)
        case Left(error) =>
          Pull.output1(Stream(socks5.code, Reply.fromError(error).code, Reserved.code, AddressType.Ipv4Address.code)
            .append(writeIpAddress(ipv4"0.0.0.0")).append(writePort(port"0"))) >>
            Pull.pure(error.asLeft)
    })

  private def readSocks5Version[F[_], O]: State[F, O, Unit] =
    for
      version <- read1[F, O](Socks5VersionEmpty)
      _ <- liftEither[F, O, Unit] {
        if version == socks5.code then ().asRight
        else UnsupportedSocksVersion(version).asLeft
      }
    yield
      ()

  private def readPasswordVersion[F[_], O]: State[F, O, Unit] =
    for
      version <- read1[F, O](PasswordVersionEmpty)
      _ <- liftEither[F, O, Unit] {
        if version == version1.code then ().asRight
        else UnsupportedPasswordVersion(version).asLeft
      }
    yield
      ()

  private def readCommand[F[_], O]: State[F, O, Command] =
    for
      cmd <- read1[F, O](CommandEmpty)
      cmd <- liftEither[F, O, Command](Command.values.find(_.code == cmd).toRight(UnsupportedCommand(cmd)))
    yield
      cmd

  private def readReserved[F[_], O]: State[F, O, Unit] =
    for
      rsv <- read1[F, O](ReservedEmpty)
      _ <- liftEither[F, O, Unit](
        if rsv == Reserved.code then ().asRight else UnsupportedReserved(rsv).asLeft
      )
    yield
      ()

  private def readAddress[F[_], O](using Charset): State[F, O, Host] =
    for
      addressType <- readAddressType[F, O]
      address <- addressType match
        case AddressType.Ipv4Address => readIpv4Address[F, O]
        case AddressType.DomainName => readDomainName[F, O]
        case AddressType.Ipv6Address => readIpv6Address[F, O]
    yield
      address

  private def readAddressType[F[_], O]: State[F, O, AddressType] =
    for
      code <- read1[F, O](AddressTypeEmpty)
      addressType <- liftEither[F, O, AddressType](
        AddressType.values.find(_.code == code).toRight(UnsupportedAddressType(code))
      )
    yield
      addressType

  private def readIpv4Address[F[_], O]: State[F, O, Ipv4Address] =
    for
      chunk <- readLimit[F, O](4, Ipv4AddressEmpty)
      ipv4Address <- liftEither[F, O, Ipv4Address](
        Ipv4Address.fromBytes(chunk.toArray).toRight(IllegalIpv4Address(chunk.toByteVector))
      )
    yield
      ipv4Address

  private def readDomainName[F[_], O](using Charset): State[F, O, Hostname] =
    for
      domainName <- readSizedString[F, O](DomainNameEmpty)
      domainName <- liftEither[F, O, Hostname](Hostname.fromString(domainName).toRight(IllegalDomainName(domainName)))
    yield
      domainName

  private def readIpv6Address[F[_], O]: State[F, O, Ipv6Address] =
    for
      chunk <- readLimit[F, O](16, Ipv6AddressEmpty)
      ipv6Address <- liftEither[F, O, Ipv6Address](
        Ipv6Address.fromBytes(chunk.toArray).toRight(IllegalIpv6Address(chunk.toByteVector))
      )
    yield
      ipv6Address

  private def writeAddress[F[_]](host: Host)(using Charset): Either[Error, Stream[F, Byte]] =
    host match
      case ipAddress: IpAddress => writeIpAddress(ipAddress).asRight
      case host => writeHost(host)

  private def writeIpAddress[F[_]](ipAddress: IpAddress): Stream[F, Byte] =
    Stream(ipAddress.toBytes*)

  private def writeHost[F[_]](host: Host)(using Charset): Either[Error, Stream[F, Byte]] =
    ByteVector.encodeString(host.toString).value(host)
      .map(bytes => Stream.chunk(Chunk.byteVector(bytes.length.toByte +: bytes)))

  private def readPort[F[_], O]: State[F, O, Port] =
    for
      chunk <- readLimit[F, O](2, PortEmpty)
      port <- liftEither[F, O, Port] {
        val port = chunk.toByteVector.toInt()
        Port.fromInt(port).toRight(IllegalPort(port))
      }
    yield
      port

  private def writePort[F[_]](port: Port): Stream[F, Byte] =
    Stream.chunk(Chunk.byteVector(ByteVector.fromInt(port.value, 2)))

  private def readSizedString[F[_], O](onEmpty: => Error)(using Charset): State[F, O, String] =
    for
      chunk <- readSizedChunk[F, O](onEmpty)
      bytes = chunk.toByteVector
      value <- liftEither[F, O, String](bytes.decodeString.value(bytes)(using Show.show(_.toHex)))
    yield
      value

  private def readSizedChunk[F[_], O](onEmpty: => Error): State[F, O, Chunk[Byte]] =
    for
      n <- read1[F, O](onEmpty)
      chunk <- readLimit[F, O](n, onEmpty)
    yield
      chunk

  private def read1[F[_], O](onEmpty: => Error): State[F, O, Byte] =
    State(_.pull.uncons1.map(_.toRight(onEmpty).map(_.swap)))

  private def readLimit[F[_], O](n: Int, onEmpty: => Error): State[F, O, Chunk[Byte]] =
    State(_.pull.unconsLimit(n).map(_.toRight(onEmpty).map(_.swap)))

  private def liftEither[F[_], O, A](either: Either[Error, A]): State[F, O, A] = StateT.liftF(Pull.pure(either))

  private def liftF[F[_], O, A](f: Pull[F, O, Either[Error, A]]): State[F, O, A] = StateT.liftF(f)

  private def unit[F[_], O]: State[F, O, Unit] = StateT.pure(())
end state
