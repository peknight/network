package com.peknight.socks5.io

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{IO, Resource}
import com.comcast.ip4s.*
import com.peknight.socks5.Command.CONNECT
import com.peknight.socks5.client.api.ClientApi
import com.peknight.socks5.client.io.Socks5Client
import com.peknight.socks5.server.api.ServerApi
import com.peknight.socks5.server.io.Socks5Server
import com.peknight.socks5.server.io.api.DirectConnectApi
import com.peknight.socks5.{Request, client, server}
import fs2.concurrent.Topic
import fs2.io.net.Network
import fs2.text.utf8
import fs2.{Pipe, Stream}
import org.scalatest.flatspec.AsyncFlatSpec

import java.time.LocalDateTime
import scala.concurrent.duration.*

class Socks5FlatSpec extends AsyncFlatSpec with AsyncIOSpec:
  "Socks5 Server" should "pass" in {

    val servicePort: Port = port"8080"
    val serviceR: Resource[IO, Stream[IO, Nothing]] =
      Network[IO].bind(SocketAddress.port(servicePort))
        .map(serverSocket => serverSocket.accept
          .map(socket => socket.reads
            .onFinalize(IO.delay(println(s"${LocalDateTime.now} service read finalized")))
            .through(socket.writes)
            .onFinalize(IO.delay(println(s"${LocalDateTime.now} service writes finalized")))
            .drain)
          .parJoinUnbounded
        )

    val socks5ServerApi = ServerApi[IO, Unit, Resource[IO, (Pipe[IO, Byte, Unit], Stream[IO, Byte])], Unit, Unit](
      server.api.NegotiationApi.noAuthenticationRequired[IO],
      server.api.UsernamePasswordApi.unsupported[IO, Unit],
      server.api.GSSApiApi.unsupported[IO, Unit],
      server.api.IANAAssignedApi.unsupported[IO, Unit],
      server.api.PrivateMethodApi.unsupported[IO, Unit],
      new DirectConnectApi[IO, Unit],
      server.api.BindApi.unsupported[IO, Unit],
      server.api.UDPAssociateApi.unsupported[IO, Unit]
    )
    val serverPort: Port = port"1088"
    val serverR: Resource[IO, Stream[IO, Nothing]] =
      Socks5Server(socks5ServerApi, SocketAddress.port(serverPort)).resource

    val localhost: Ipv4Address = ipv4"127.0.0.1"
    val request: Request = Request(CONNECT, localhost, servicePort)
    val text: String = "Hello, Socks5!"
    val input: Stream[IO, Byte] = Stream[IO, String](text).through(utf8.encode[IO])

    val clientR: Resource[IO, Stream[IO, Byte]] =
      Resource.eval(Topic[IO, Byte]).flatMap { topic =>
      val socks5ClientApi = ClientApi[IO, Unit, Resource[IO, (Pipe[IO, Byte, Unit], Stream[IO, Byte])], Unit, Unit](
        client.api.NegotiationApi.noAuthenticationRequired[IO],
        client.api.UsernamePasswordApi.unsupported[IO, Unit],
        client.api.GSSApiApi.unsupported[IO, Unit],
        client.api.IANAAssignedApi.unsupported[IO, Unit],
        client.api.PrivateMethodApi.unsupported[IO, Unit],
        client.api.RequestApi[IO, Unit](request),
        client.api.ConnectApi[IO, Unit](input, topic.publish),
        client.api.BindApi.unsupported[IO, Unit],
        client.api.UDPAssociateApi.unsupported[IO, Unit]
      )
      Socks5Client(socks5ClientApi, SocketAddress(localhost, serverPort)).resource
        .map(stream => topic.subscribeUnbounded
          .onFinalize(IO.delay(println(s"${LocalDateTime.now} client spec subscribe finalized")))
          .merge(stream.onFinalize(IO.delay(println(s"${LocalDateTime.now} client spec stream finalized"))))
          .onFinalize(IO.delay(println(s"${LocalDateTime.now} client spec merge finalized")))
        )
    }

    val resource: Resource[IO, Stream[IO, Byte]] =
      for
        service <- serviceR
        server <- serverR
        client <- clientR
      yield
        client
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
      .interruptAfter(5.seconds)
      .compile
      .toList
      .map(_.mkString)
      .asserting(value => assert(value === text))
  }
end Socks5FlatSpec
