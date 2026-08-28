package com.peknight.socks5

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{IO, Resource}
import com.comcast.ip4s.*
import com.peknight.ip4s.HostPort
import com.peknight.socks.Socket
import com.peknight.socks5.client.Socks5Client
import com.peknight.socks5.server.Socks5Server
import com.peknight.socks5.server.api.ServerApi
import fs2.io.net.Network
import fs2.text.utf8
import fs2.{Pipe, Stream}
import org.scalatest.flatspec.AsyncFlatSpec

class Socks5FlatSpec extends AsyncFlatSpec with AsyncIOSpec:
  "Socks5 Server" should "pass" in {
    val localHost: Ipv4Address = ipv4"127.0.0.1"
    val servicePort: Port = port"8796"
    val serverPort: Port = port"1798"
    val text: String = "Hello, Socks5!"
    val input: Stream[IO, Byte] = Stream[IO, String](text).through(utf8.encode[IO])
    val serviceR: Resource[IO, Stream[IO, Nothing]] =
      Network[IO].bind(SocketAddress.port(servicePort)).map(serverSocket => serverSocket.accept.map(socket => socket
        .reads
        .onFinalize(socket.endOfInput)
        .through(socket.writes)
        .onFinalize(socket.endOfOutput)
        .drain
      ).parJoinUnbounded)
    val socks5ServerApi = ServerApi[IO, Unit, Resource[IO, Socket[IO]], Unit, Unit](
      server.api.NegotiationApi.noAuthenticationRequired[IO],
      server.api.UsernamePasswordApi.unsupported[IO, Unit],
      server.api.GSSApiApi.unsupported[IO, Unit],
      server.api.IANAAssignedApi.unsupported[IO, Unit],
      server.api.PrivateMethodApi.unsupported[IO, Unit],
      server.api.ConnectApi.direct[IO, Unit],
      server.api.BindApi.unsupported[IO, Unit],
      server.api.UDPAssociateApi.unsupported[IO, Unit]
    )
    val serverR: Resource[IO, Stream[IO, Nothing]] =
      Socks5Server(socks5ServerApi, SocketAddress.port(serverPort)).resource

    val pipe: Pipe[IO, Byte, Byte] =
      Socks5Client.connect[IO, Unit](HostPort(localHost, servicePort), HostPort(localHost, serverPort))(
        client.api.NegotiationApi.noAuthenticationRequired[IO],
        client.api.UsernamePasswordApi.unsupported[IO, Unit],
        client.api.GSSApiApi.unsupported[IO, Unit],
        client.api.IANAAssignedApi.unsupported[IO, Unit],
        client.api.PrivateMethodApi.unsupported[IO, Unit]
      )

    val stream: Stream[IO, Byte] =
      for
        service <- Stream.resource(serviceR)
        server <- Stream.resource(serverR)
        b <- Stream[IO, String](text).through(utf8.encode[IO])
          .through(pipe)
          .mergeHaltBoth(service.merge(server))
      yield
        b
    stream.through(utf8.decode).compile.toList.map(_.mkString).asserting(value => assert(value === text))
  }
end Socks5FlatSpec
