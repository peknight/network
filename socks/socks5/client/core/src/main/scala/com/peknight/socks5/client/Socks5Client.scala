package com.peknight.socks5.client

import cats.effect.std.Queue
import cats.effect.{Async, Resource}
import com.comcast.ip4s.{GenSocketAddress, SocketAddress}
import com.peknight.cats.instances.eitherT.given
import com.peknight.ip4s.HostPort
import com.peknight.socks.Connection
import com.peknight.socks5.client.api.*
import com.peknight.socks5.client.state.ClientPullState
import com.peknight.socks5.state.State.Initial
import fs2.io.net.{Network, Socket, SocketOption}
import fs2.{Chunk, Pipe, Stream}

import java.nio.charset.{Charset, StandardCharsets}
import java.time.LocalDateTime

trait Socks5Client[F[_], Auth, ConnectState, BindState, UDPAssociateState](using Charset)(using Async[F]):
  def api: ClientApi[F, Auth, ConnectState, BindState, UDPAssociateState]
  def connect: Resource[F, Socket[F]]
  def resource: Resource[F, Stream[F, Nothing]] =
    connect.map(socket => ClientPullState(api)
      .run((Initial(Connection(socket.address, socket.peerAddress, socket.endOfInput, socket.endOfOutput)),
        socket.reads))
      .as(())
      .stream
      .onFinalize(Async[F].delay(println(s"${LocalDateTime.now} client resource stream finalized")))
      .through(socket.writes)
      .onFinalize(Async[F].delay(println(s"${LocalDateTime.now} client resource writes finalized")))
      .attempt
      .drain
    )
  def run: Stream[F, Nothing] = Stream.resource(resource).flatten
end Socks5Client
object Socks5Client:
  private case class Socks5Client[F[_], Auth, ConnectState, BindState, UDPAssociateState](
    api: ClientApi[F, Auth, ConnectState, BindState, UDPAssociateState],
    address: GenSocketAddress,
    options: List[SocketOption] = Nil,
    charset: Charset = StandardCharsets.UTF_8
  )(using Async[F], Network[F])
    extends com.peknight.socks5.client.Socks5Client[F, Auth, ConnectState, BindState, UDPAssociateState](using charset):
    def connect: Resource[F, Socket[F]] = Network[F].connect(address, options)
  end Socks5Client
  def apply[F[_], Auth, ConnectState, BindState, UDPAssociateState](
    api: ClientApi[F, Auth, ConnectState, BindState, UDPAssociateState],
    address: GenSocketAddress,
    options: List[SocketOption] = Nil, charset: Charset = StandardCharsets.UTF_8
  )(using Async[F], Network[F])
  : com.peknight.socks5.client.Socks5Client[F, Auth, ConnectState, BindState, UDPAssociateState] =
    Socks5Client(api, address, options)

  def connect[F[_]: {Async, Network}, Auth]
             (requestAddress: HostPort, serverAddress: HostPort,
              options: List[SocketOption] = Nil, charset: Charset = StandardCharsets.UTF_8)
             (negotiationApi: NegotiationApi[F, Auth],
              usernamePasswordApi: UsernamePasswordApi[F, Auth],
              gssApiApi: GSSApiApi[F, Auth],
              ianaAssignedApi: IANAAssignedApi[F, Auth],
              privateMethodApi: PrivateMethodApi[F, Auth])
  : Pipe[F, Byte, Byte] = in =>
    Stream.eval(Queue.unbounded[F, Option[Chunk[Byte]]]).flatMap { queue =>
      val api = ClientApi[F, Auth, (Stream[F, Byte], Queue[F, Option[Chunk[Byte]]]), Unit, Unit](
        negotiationApi,
        usernamePasswordApi,
        gssApiApi,
        ianaAssignedApi,
        privateMethodApi,
        RequestApi.connect[F, Auth](requestAddress),
        ConnectApi.queue[F, Auth](in, queue),
        BindApi.unsupported[F, Auth],
        UDPAssociateApi.unsupported[F, Auth]
      )
      Stream.fromQueueNoneTerminatedChunk(queue)
        .merge(Socks5Client(api, SocketAddress(serverAddress.host, serverAddress.port), options, charset).run)
    }

end Socks5Client
