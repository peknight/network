package com.peknight.socks

import cats.Applicative
import com.comcast.ip4s.GenSocketAddress
import fs2.{Pipe, Stream}

case class Connection[F[_]](address: GenSocketAddress, peerAddress: GenSocketAddress,
                            writes: Pipe[F, Byte, Nothing], endOfInput: F[Unit], endOfOutput: F[Unit]):
  /**
   * 将 tunnel 读到的数据写入对端，并在写尽后半关闭对端的输出方向（发 FIN）。
   *
   * 关键点：`endOfOutput` 必须挂在 `.through(writes)` 之后、与写处于同一条线性链上，
   * 不能挂在 merge 的“读源分支”上——否则 FIN 可能先于最后一块数据的实际写入执行，
   * 造成对端只收到 FIN 而丢数据（并发 merge 中分支终结器不等待下游 sink 消费完）。
   */
  def relayWrites(reads: Stream[F, Byte])(using Applicative[F]): Stream[F, Nothing] =
    reads.through(writes).onFinalize(endOfOutput).drain
end Connection
object Connection:
  def apply[F[_]](socket: fs2.io.net.Socket[F]): Connection[F] =
    Connection(socket.address, socket.peerAddress, socket.writes, socket.endOfInput, socket.endOfOutput)
