package com.peknight.socks5.server.io

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{IO, Resource}
import com.comcast.ip4s.{SocketAddress, port}
import com.peknight.socks5.server.api.*
import com.peknight.socks5.server.io.api.DirectConnectApi
import fs2.{Pipe, Stream}
import org.scalatest.flatspec.AsyncFlatSpec

import scala.concurrent.duration.*

class Socks5ServerFlatSpec extends AsyncFlatSpec with AsyncIOSpec:
  "Socks5 Server" should "pass" in {
    val socks5ServerApi = ServerApi[IO, Unit, Resource[IO, (Pipe[IO, Byte, Unit], Stream[IO, Byte])], Unit, Unit](
      NegotiationApi.noAuthenticationRequired[IO],
      UsernamePasswordApi.unsupported[IO, Unit],
      GSSApiApi.unsupported[IO, Unit],
      IANAAssignedApi.unsupported[IO, Unit],
      PrivateMethodApi.unsupported[IO, Unit],
      new DirectConnectApi[IO, Unit],
      BindApi.unsupported[IO, Unit],
      UDPAssociateApi.unsupported[IO, Unit]
    )
    val socks5Server = Socks5Server(socks5ServerApi, SocketAddress.port(port"1088"))
    // serve 是常驻流，测试只验证其能正常拉起，10 秒后中断以让用例结束
    socks5Server.serve.interruptAfter(10.seconds).compile.drain.asserting(_ => assert(true))
  }
end Socks5ServerFlatSpec
