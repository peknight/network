package com.peknight.socks

import cats.Applicative
import cats.syntax.applicative.*
import fs2.{Pipe, Stream}

case class Socket[F[_]](reads: Stream[F, Byte], writes: Pipe[F, Byte, Nothing], endOfInput: F[Unit], endOfOutput: F[Unit])
object Socket:
  def apply[F[_]](socket: fs2.io.net.Socket[F]): Socket[F] =
    Socket(socket.reads, socket.writes, socket.endOfInput, socket.endOfOutput)
  def empty[F[_]: Applicative]: Socket[F] = Socket(Stream.empty, _.drain, ().pure[F], ().pure[F])
end Socket
