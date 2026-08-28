package com.peknight.socks5.io

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{IO, Resource}
import cats.effect.std.Supervisor
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

import scala.concurrent.duration.*

class Socks5FlatSpec extends AsyncFlatSpec with AsyncIOSpec:
  "Socks5 Server" should "pass" in {

    val servicePort: Port = port"8080"
    val service: Stream[IO, Nothing] = Network[IO].bindAndAccept(SocketAddress.port(servicePort))
      .map(socket => socket.reads.through(socket.writes).drain)
      .parJoinUnbounded

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
    val serve: Stream[IO, Nothing] = Socks5Server(socks5ServerApi, SocketAddress.port(serverPort)).serve

    val localhost: Ipv4Address = ipv4"127.0.0.1"
    val request: Request = Request(CONNECT, localhost, servicePort)
    val text: String = "Hello, Socks5!"
    val input: Stream[IO, Byte] = Stream[IO, String](text).through(utf8.encode[IO])
    val stream: Stream[IO, Byte] = Stream.eval(Topic[IO, Byte]).flatMap { topic =>
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
      topic.subscribeUnbounded.concurrently(Socks5Client(socks5ClientApi, SocketAddress(localhost, serverPort)).run)
    }
    val test: IO[String] = Supervisor[IO](await = false).use { supervisor =>
      supervisor.supervise(service.concurrently(serve).compile.drain) *>
      IO.sleep(200.millis) *> // Wait for servers to be ready
      stream.through(utf8.decode[IO])
        .interruptAfter(5.seconds)
        .compile
        .toList
        .map(_.mkString)
    }

    test.asserting(value => assert(value === text))
  }
end Socks5FlatSpec
