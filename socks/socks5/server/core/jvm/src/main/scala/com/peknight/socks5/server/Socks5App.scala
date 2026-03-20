package com.peknight.socks5.server

import cats.Monad
import cats.effect.std.Console
import cats.effect.{Concurrent, IO, IOApp}
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import com.comcast.ip4s.{SocketAddress, host, port}
import fs2.Stream
import fs2.io.net.{Network, Socket}
import fs2.text.hex
import scodec.bits.ByteVector

object Socks5App extends IOApp.Simple:

  def server[F[_]: {Concurrent, Network, Console}]: F[Unit] =
    Network[F].bindAndAccept(SocketAddress.port(port"1088"))
      .evalMap { client =>
        Network[F].connect(SocketAddress(host"127.0.0.1", port"1080"))
          .allocated
          .flatTap((target, _) => Console[F].println(s"${showPorts(client, target)} connected"))
          .map { (target, release) =>
            val ports = showPorts(client, target)
            Stream(
              client.reads
                .through(hex.encode[F])
                .evalTap(h => Console[F].println(s"$ports request: $h"))
                .through(hex.decode[F])
                .through(target.writes),
              target.reads
                .through(hex.encode[F])
                .evalTap(h => Console[F].println(s"$ports response: $h"))
                .through(hex.decode[F])
                .through(client.writes)
            ).parJoin(2).onFinalize {
              for
                _ <- release
                _ <- Console[F].println(s"$ports released")
              yield
                ()
            }
          }
      }
      .parJoin(100).compile.drain

  def showPorts[F[_]: Monad](client: Socket[F], target: Socket[F]): String =
    s"${client.peerAddress.asIpUnsafe} -> ${target.address.asIpUnsafe}"

  val run: IO[Unit] =
    for
      _ <- IO.println(ByteVector(0x12, 0x34).toInt())
    yield
      ()
end Socks5App
