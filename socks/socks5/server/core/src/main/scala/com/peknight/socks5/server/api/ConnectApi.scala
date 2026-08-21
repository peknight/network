package com.peknight.socks5.server.api

import cats.Applicative
import cats.effect.{MonadCancel, Resource}
import com.peknight.socks5.Response
import com.peknight.socks5.State.{Connected, Requested}
import com.peknight.socks5.server.state.unsupportedCommand
import fs2.{Pipe, Stream}

trait ConnectApi[F[_], Auth, ConnectState]:
  def connect(state: Requested[Auth]): F[(Response, ConnectState)]
  def connectSend(state: Connected[Auth, ConnectState]): Pipe[F, Byte, Unit]
  def connectReceive(state: Connected[Auth, ConnectState]): Stream[F, Byte]
end ConnectApi
object ConnectApi:
  private class UnsupportedConnectApi[F[_]: Applicative, Auth] extends ConnectApi[F, Auth, Unit]:
    def connect(state: Requested[Auth]): F[(Response, Unit)] = unsupportedCommand(state, ())
    def connectSend(state: Connected[Auth, Unit]): Pipe[F, Byte, Unit] = _.drain
    def connectReceive(state: Connected[Auth, Unit]): Stream[F, Byte] = Stream.empty
  end UnsupportedConnectApi
  trait ResourceConnectApi[F[_], Auth](using MonadCancel[F, ?])
    extends ConnectApi[F, Auth, Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]]:
    def connectSend(state: Connected[Auth, Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]]): Pipe[F, Byte, Unit] =
      in => Stream.resource(state.state.map(_._1)).flatMap(in.through)
    def connectReceive(state: Connected[Auth, Resource[F, (Pipe[F, Byte, Unit], Stream[F, Byte])]]): fs2.Stream[F, Byte] =
      Stream.resource(state.state.map(_._2)).flatten
  end ResourceConnectApi
  def unsupported[F[_]: Applicative, Auth]: ConnectApi[F, Auth, Unit] = new UnsupportedConnectApi[F, Auth]
end ConnectApi
