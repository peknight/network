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

import java.time.LocalDateTime
import scala.concurrent.duration.*

class Socks5FlatSpec extends AsyncFlatSpec with AsyncIOSpec:
  "Socks5 Server" should "pass" in {

    val localHost: Ipv4Address = ipv4"127.0.0.1"
    val servicePort: Port = port"8080"
    val serviceAddress: GenSocketAddress = SocketAddress(localHost, servicePort)
    val serviceR: Resource[IO, Stream[IO, Nothing]] =
      Network[IO].bind(SocketAddress.port(servicePort))
        .map(serverSocket => serverSocket.accept
          .map(socket => socket.reads
            .observe(in => in.through(utf8.decode).evalTap(s => IO.println(s"${LocalDateTime.now} service read: $s")).drain)
            .onFinalize(socket.endOfInput)
            .onFinalize(IO.delay(println(s"${LocalDateTime.now} service read finalized")))
            .through(socket.writes)
            .onFinalize(socket.endOfOutput)
            .onFinalize(IO.delay(println(s"${LocalDateTime.now} service writes finalized")))
            .drain)
          .parJoinUnbounded
        )

    val text: String = "Hello, Socks5!"
    val input: Stream[IO, Byte] = Stream[IO, String](text).through(utf8.encode[IO])

    val directR: Resource[IO, Stream[IO, Byte]] = Network[IO].connect(serviceAddress)
      .map(socket => socket.reads
        .observe(in => in.through(utf8.decode).evalTap(s => IO.println(s"${LocalDateTime.now} direct read: $s")).drain)
        .onFinalize(IO.delay(println(s"${LocalDateTime.now} direct read finalized")))
        .merge(input
          .observe(in => in.through(utf8.decode).evalTap(s => IO.println(s"direct input: $s")).drain)
          .onFinalize(socket.endOfInput)
          .onFinalize(IO.delay(println(s"${LocalDateTime.now} direct input finalized")))
          .through(socket.writes)
          .onFinalize(socket.endOfOutput)
          .onFinalize(IO.delay(println(s"${LocalDateTime.now} direct write finalized")))
        )
      )

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
    val serverPort: Port = port"1088"
    val serverAddress: GenSocketAddress = SocketAddress(localHost, serverPort)
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

    val resource: Resource[IO, Stream[IO, Byte]] =
      for
        service <- serviceR
        server <- serverR
      yield
        input.through(pipe)
          .onFinalize(IO.delay(println(s"${LocalDateTime.now} clientR spec finalized")))
          .mergeHaltBoth(service
            .onFinalize(IO.delay(println(s"${LocalDateTime.now} serviceR spec finalized")))
            .merge(server
              .onFinalize(IO.delay(println(s"${LocalDateTime.now} serverR spec finalized"))))
            .onFinalize(IO.delay(println(s"${LocalDateTime.now} serviceR merge serverR spec finalized")))
          )
          .onFinalize(IO.delay(println(s"${LocalDateTime.now} clientR serviceR serverR spec finalized")))
    Stream.resource(resource)
      .flatten
      .through(utf8.decode)
      .interruptAfter(3.seconds)
      .compile
      .toList
      .map(_.mkString)
      .asserting(value => assert(value === text))
  }
end Socks5FlatSpec
