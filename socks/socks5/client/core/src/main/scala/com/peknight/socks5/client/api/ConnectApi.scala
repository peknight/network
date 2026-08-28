package com.peknight.socks5.client.api

import cats.effect.Resource
import cats.effect.std.Queue
import cats.syntax.applicative.*
import cats.syntax.option.*
import cats.{Applicative, ApplicativeError}
import com.peknight.socks.Socket
import com.peknight.socks5.Response
import com.peknight.socks5.state.State.{Connected, Requested}
import fs2.{Chunk, Stream}

trait ConnectApi[F[_], Auth, ConnectState]:
  def connect(state: Requested[F, Auth], response: Response): F[ConnectState]
  def tunnel(state: Connected[F, Auth, ConnectState]): Resource[F, Socket[F]]
end ConnectApi
object ConnectApi:
  private case class QueueConnectApi[F[_]: Applicative, Auth](reads: Stream[F, Byte], queue: Queue[F, Option[Chunk[Byte]]])
    extends ConnectApi[F, Auth, (Stream[F, Byte], Queue[F, Option[Chunk[Byte]]])]:
    def connect(state: Requested[F, Auth], response: Response): F[(Stream[F, Byte], Queue[F, Option[Chunk[Byte]]])] =
      (reads, queue).pure[F]
    def tunnel(state: Connected[F, Auth, (Stream[F, Byte], Queue[F, Option[Chunk[Byte]]])]): Resource[F, Socket[F]] =
      Resource.pure(Socket(state.state._1,
        in => in.chunks.evalMap(chunk => queue.offer(chunk.some)).onFinalize(queue.offer(None)).drain,
        ().pure[F], ().pure[F]))
  end QueueConnectApi
  private class UnsupportedConnectApi[F[_], Auth](using ApplicativeError[F, Throwable])
    extends com.peknight.socks5.client.api.ConnectApi[F, Auth, Unit]:
    def connect(state: Requested[F, Auth], response: Response): F[Unit] =
      unsupportedCommand(state, response)
    def tunnel(state: Connected[F, Auth, Unit]): Resource[F, Socket[F]] =
      Resource.pure(Socket.empty[F])
  end UnsupportedConnectApi

  def queue[F[_]: Applicative, Auth](reads: Stream[F, Byte], queue: Queue[F, Option[Chunk[Byte]]])
  : ConnectApi[F, Auth, (Stream[F, Byte], Queue[F, Option[Chunk[Byte]]])] =
    QueueConnectApi[F, Auth](reads, queue)
  def unsupported[F[_], Auth](using ApplicativeError[F, Throwable])
  : com.peknight.socks5.client.api.ConnectApi[F, Auth, Unit] =
    new UnsupportedConnectApi[F, Auth]
end ConnectApi
