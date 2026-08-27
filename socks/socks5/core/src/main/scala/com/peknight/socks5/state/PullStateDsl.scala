package com.peknight.socks5.state

import cats.syntax.either.*
import com.comcast.ip4s.*
import com.peknight.cats.instances.eitherT.given
import com.peknight.error.Error
import com.peknight.error.std.WrongClassTag
import com.peknight.error.syntax.either.value
import com.peknight.fs2.pull.state.BytePullStateErrorDsl
import com.peknight.socks.SocksVersion
import com.peknight.socks.SocksVersion.socks5
import com.peknight.socks.error.UnsupportedSocksVersion
import com.peknight.socks5.auth.password.PasswordVersion.version1
import com.peknight.socks5.error.*
import com.peknight.socks5.state.State.{AuthRequiredMethodSelected, Requested, Terminated}
import com.peknight.socks5.{AddressType, Reserved}
import scodec.bits.ByteVector

import java.nio.charset.Charset
import scala.reflect.ClassTag

/**
 * SOCKS5 状态机专用的 [[BytePullStateErrorDsl]]：
 * 固定 `S = State`、`E = Terminated`，底层 `Throwable` 统一通过 `State.error` 提升。
 */
trait PullStateDsl extends BytePullStateErrorDsl[State, Terminated]:

  def unsupportedMethod[F[_], A]: Aux[F, A] =
    for
      state <- typedS[F, AuthRequiredMethodSelected]
      a <- liftL[F, A](state.unsupportedMethod)
    yield
      a

  def unsupportedCommand[F[_], A]: Aux[F, A] =
    for
      state <- typedS[F, Requested[?]]
      a <- liftL[F, A](state.unsupportedCommand)
    yield
      a

  private[socks5] def readSocks5Version[F[_]]: Aux[F, SocksVersion] =
    parse1[F, SocksVersion](version =>
      if version == socks5.code then socks5.asRight
      else UnsupportedSocksVersion(version).asLeft
    )(Socks5VersionEof)

  private[socks5] def readPasswordVersion[F[_]]: Aux[F, Unit] =
    parse1[F, Unit](version =>
      if version == version1.code then ().asRight
      else UnsupportedPasswordVersion(version).asLeft
    )(PasswordVersionEof)

  private[socks5] def readReserved[F[_]]: Aux[F, Unit] =
    parse1[F, Unit](rsv =>
      if rsv == Reserved.code then ().asRight
      else UnsupportedReserved(rsv).asLeft
    )(ReservedEof)

  private[socks5] def readAddress[F[_]](using Charset): Aux[F, (Host, ByteVector)] =
    for
      addressType <- readAddressType[F]
      addressTuple <- addressType match
        case AddressType.Ipv4Address => readIpv4Address[F]
        case AddressType.DomainName => readDomainName[F]
        case AddressType.Ipv6Address => readIpv6Address[F]
    yield
      addressTuple

  private def readAddressType[F[_]]: Aux[F, AddressType] =
    parse1[F, AddressType](code =>
      AddressType.values.find(_.code == code).toRight(UnsupportedAddressType(code))
    )(AddressTypeEof)

  private def readIpv4Address[F[_]]: Aux[F, (Ipv4Address, ByteVector)] =
    parseChunk[F, (Ipv4Address, ByteVector)](_.unconsN(4))(chunk =>
      Ipv4Address.fromBytes(chunk.toArray)
        .map((_, chunk.toByteVector))
        .toRight(IllegalIpv4Address(chunk.toByteVector))
    )(Ipv4AddressEof)

  private def readDomainName[F[_]](using Charset): Aux[F, (Hostname, ByteVector)] =
    parseSizedStringBytes[F, Hostname](domainName =>
      Hostname.fromString(domainName).toRight(IllegalDomainName(domainName))
    )(DomainNameEof)

  private def readIpv6Address[F[_]]: Aux[F, (Ipv6Address, ByteVector)] =
    parseChunk[F, (Ipv6Address, ByteVector)](_.unconsN(16))(chunk =>
      Ipv6Address.fromBytes(chunk.toArray)
        .map((_, chunk.toByteVector))
        .toRight(IllegalIpv6Address(chunk.toByteVector))
    )(Ipv6AddressEof)

  private[socks5] def readPort[F[_]]: Aux[F, Port] =
    parseChunk[F, Port](_.unconsN(2)) { chunk =>
      val port = chunk.toByteVector.toInt()
      Port.fromInt(port).toRight(IllegalPort(port))
    }(PortEof)

  private[socks5] def encodeAddress(host: Host)(using Charset): Either[Error, ByteVector] =
    host match
      case ipAddress: IpAddress => encodeIpAddress(ipAddress).asRight
      case host => encodeSizedString(host.toString)

  private[socks5] def encodeIpAddress(ipAddress: IpAddress): ByteVector = ByteVector(ipAddress.toBytes)

  private[socks5] def encodePort(port: Port): ByteVector = ByteVector.fromInt(port.value, 2)

  def error(state: State, throwable: Throwable): Terminated = state.error(throwable)

  override def setS[F[_]](s: State): Aux[F, Unit] =
    s match
      case terminated: Terminated => liftL(terminated)
      case state => super.setS(state)

  def typed[F[_], A: ClassTag](any: Any): Aux[F, A] =
    super.typed[F, Any, A](any)((s, a) => s.error(WrongClassTag[A](a)))

  def typedS[F[_], A: ClassTag]: Aux[F, A] =
    super.typedS(s => s.error(WrongClassTag[A](s)))

  private def parseSizedStringBytes[F[_], A](f: String => Either[Throwable, A])(eof: => Throwable)(using Charset)
  : Aux[F, (A, ByteVector)] =
    for
      bytes <- readSizedBytes[F](eof)
      value <- liftET(bytes.toByteVector.decodeString.flatMap(f))
    yield
      (value, bytes.toByteVector)

  def encodeSizedString(value: String)(using Charset): Either[Error, ByteVector] =
    ByteVector.encodeString(value).value(value).map(bytes => bytes.length.toByte +: bytes)

  extension [F[_]](state: Aux[F, State])
    def outputS(f: State => ByteVector): Aux[F, State] =
      state.attempt.outputE(either => f(either.fold[State](identity, identity)))
  end extension
end PullStateDsl
