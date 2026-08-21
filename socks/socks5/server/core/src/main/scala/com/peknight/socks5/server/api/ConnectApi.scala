package com.peknight.socks5.server.api

import cats.Applicative
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
  def unsupported[F[_]: Applicative, Auth]: ConnectApi[F, Auth, Unit] = new UnsupportedConnectApi[F, Auth]
end ConnectApi
