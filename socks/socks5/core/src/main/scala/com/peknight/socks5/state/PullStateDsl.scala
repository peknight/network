package com.peknight.socks5.state

import cats.effect.{Concurrent, Resource}
import cats.syntax.either.*
import com.comcast.ip4s.*
import com.peknight.cats.instances.eitherT.given
import com.peknight.error.Error
import com.peknight.error.std.WrongClassTag
import com.peknight.error.syntax.either.value
import com.peknight.fs2.pull.state.BytePullStateErrorDsl
import com.peknight.socks.SocksVersion.socks5
import com.peknight.socks.error.UnsupportedSocksVersion
import com.peknight.socks.{Socket, SocksVersion}
import com.peknight.socks5.auth.password.PasswordVersion.version1
import com.peknight.socks5.error.*
import com.peknight.socks5.state.State.*
import com.peknight.socks5.{AddressType, Reserved}
import fs2.Stream
import scodec.bits.ByteVector

import java.nio.charset.Charset
import scala.reflect.ClassTag

/**
 * SOCKS5 状态机专用的 [[BytePullStateErrorDsl]]：
 * 固定 `S = State`、`E = Terminated`，底层 `Throwable` 统一通过 `State.error` 提升。
 */
trait PullStateDsl[F[_]] extends BytePullStateErrorDsl[F, State[F], Terminated[F]]:

  private[socks5] def established[Auth, ConnectState, BindState, UDPAssociateState]
                                 (tunnel: Connected[F, Auth, ConnectState] => Resource[F, Socket[F]])
                                 (bound: Aux[Unit], udpAssociated: Aux[Unit])(using Concurrent[F]): Aux[Unit] =
    getS.flatMap {
      case _: Connected[?, ?, ?] => connected[Auth, ConnectState](tunnel)
      case _: Bound[?, ?, ?] => bound
      case _: UDPAssociated[?, ?, ?] => udpAssociated
      case state => liftT[Unit](WrongClassTag[RespondedSuccessState[?, ?, ?]](state))
    }

  private def connected[Auth, ConnectState](tunnel: Connected[F, Auth, ConnectState] => Resource[F, Socket[F]])
                                           (using Concurrent[F]): Aux[Unit] =
    for
      connected <- typedS[Connected[F, Auth, ConnectState]]
      _ <- pipe(input => Stream
        .resource[F, Socket[F]](tunnel(connected))
        .flatMap(socket => socket.reads
          .through(connected.connection.writes)
          .onFinalize(connected.connection.endOfOutput)
          .drain
          .merge(input.through(socket.writes).onFinalize(socket.endOfOutput).drain)))
        .attempt
      _ <- setS(connected.closed)
    yield
      ()

  private[socks5] def readSocks5Version: Aux[SocksVersion] =
    parse1[SocksVersion](version =>
      if version == socks5.code then socks5.asRight
      else UnsupportedSocksVersion(version).asLeft
    )(Socks5VersionEof)

  private[socks5] def readPasswordVersion: Aux[Unit] =
    parse1[Unit](version =>
      if version == version1.code then ().asRight
      else UnsupportedPasswordVersion(version).asLeft
    )(PasswordVersionEof)

  private[socks5] def readReserved: Aux[Unit] =
    parse1[Unit](rsv =>
      if rsv == Reserved.code then ().asRight
      else UnsupportedReserved(rsv).asLeft
    )(ReservedEof)

  private[socks5] def readAddress(using Charset): Aux[(Host, ByteVector)] =
    for
      addressType <- readAddressType
      addressTuple <- addressType match
        case AddressType.Ipv4Address => readIpv4Address
        case AddressType.DomainName => readDomainName
        case AddressType.Ipv6Address => readIpv6Address
    yield
      addressTuple

  private def readAddressType: Aux[AddressType] =
    parse1[AddressType](code =>
      AddressType.values.find(_.code == code).toRight(UnsupportedAddressType(code))
    )(AddressTypeEof)

  private def readIpv4Address: Aux[(Ipv4Address, ByteVector)] =
    parseChunk[(Ipv4Address, ByteVector)](_.unconsN(4))(chunk =>
      Ipv4Address.fromBytes(chunk.toArray)
        .map((_, chunk.toByteVector))
        .toRight(IllegalIpv4Address(chunk.toByteVector))
    )(Ipv4AddressEof)

  private def readDomainName(using Charset): Aux[(Hostname, ByteVector)] =
    parseSizedStringBytes[Hostname](domainName =>
      Hostname.fromString(domainName).toRight(IllegalDomainName(domainName))
    )(DomainNameEof)

  private def readIpv6Address: Aux[(Ipv6Address, ByteVector)] =
    parseChunk[(Ipv6Address, ByteVector)](_.unconsN(16))(chunk =>
      Ipv6Address.fromBytes(chunk.toArray)
        .map((_, chunk.toByteVector))
        .toRight(IllegalIpv6Address(chunk.toByteVector))
    )(Ipv6AddressEof)

  private[socks5] def readPort: Aux[Port] =
    parseChunk[Port](_.unconsN(2)) { chunk =>
      val port = chunk.toByteVector.toInt(signed = false)
      Port.fromInt(port).toRight(IllegalPort(port))
    }(PortEof)

  private[socks5] def encodeAddress(host: Host)(using Charset): Either[Error, ByteVector] =
    host match
      case ipAddress: IpAddress => encodeIpAddress(ipAddress).asRight
      case host => encodeSizedString(host.toString)

  private[socks5] def encodeIpAddress(ipAddress: IpAddress): ByteVector = ByteVector(ipAddress.toBytes)

  private[socks5] def encodePort(port: Port): ByteVector = ByteVector.fromInt(port.value, 2)

  def error(state: State[F], throwable: Throwable): Terminated[F] = state.error(throwable)

  override def setS(s: State[F]): Aux[Unit] =
    s match
      case terminated: Terminated[F] => liftL(terminated)
      case state => super.setS(state)

  def typed[A: ClassTag](any: Any): Aux[A] =
    super.typed[Any, A](any)((s, a) => s.error(WrongClassTag[A](a)))

  def typedS[A: ClassTag]: Aux[A] =
    super.typedS(s => s.error(WrongClassTag[A](s)))

  private def parseSizedStringBytes[A](f: String => Either[Throwable, A])(eof: => Throwable)(using Charset)
  : Aux[(A, ByteVector)] =
    for
      bytes <- readSizedBytes(eof)
      value <- liftET(bytes.toByteVector.decodeString.flatMap(f))
    yield
      (value, bytes.toByteVector)

  def encodeSizedString(value: String)(using Charset): Either[Error, ByteVector] =
    ByteVector.encodeString(value).value(value).map(bytes => bytes.length.toByte +: bytes)

  extension (state: Aux[State[F]])
    def outputS(f: State[F] => ByteVector): Aux[State[F]] =
      state.attempt.outputE(either => f(either.fold[State[F]](identity, identity)))
  end extension
end PullStateDsl
object PullStateDsl:
  private class PullStateDsl[F[_]] extends com.peknight.socks5.state.PullStateDsl[F]
  def apply[F[_]]: com.peknight.socks5.state.PullStateDsl[F] = new PullStateDsl[F]
end PullStateDsl
