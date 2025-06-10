package com.peknight.socks5.server

import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.port
import fs2.*
import fs2.io.net.*

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.channels.ServerSocketChannel

object Socks5Proxy { //extends IOApp {

  // // SOCKS5常量定义
  // object Socks5 {
  //   val Version = 0x05
  //   val MethodNoAuth = 0x00
  //   val MethodNotAvailable = 0xFF
  //   val CmdConnect = 0x01
  //   val AddrTypeIPv4 = 0x01
  //   val AddrTypeDomain = 0x03
  //   val ReplySuccess = 0x00
  //   val ReplyGeneralFailure = 0x01
  // }

  // // 客户端认证请求
  // case class AuthRequest(version: Byte, nMethods: Byte, methods: List[Byte])
  // // 服务器认证响应
  // case class AuthResponse(version: Byte, method: Byte)
  // // 客户端连接请求
  // case class ConnectRequest(
  //                            version: Byte,
  //                            cmd: Byte,
  //                            rsv: Byte,
  //                            addrType: Byte,
  //                            address: String,
  //                            port: Int
  //                          )
  // // 服务器连接响应
  // case class ConnectResponse(
  //                             version: Byte,
  //                             reply: Byte,
  //                             rsv: Byte,
  //                             addrType: Byte,
  //                             address: String,
  //                             port: Int
  //                           )

  // def run(args: List[String]): IO[ExitCode] = {
  //   Network.forAsync[IO]
  //     .server(port = Some(port"1088"))
  //     .map(handleConnection)
  //     .compile
  //     .drain
  //     .as(ExitCode.Success)
  //     .handleErrorWith { e =>
  //       IO.println(s"Server error: ${e.getMessage}") *>
  //         IO(ExitCode.Error)
  //     }
  // }

  // def handleConnection(clientSocket: Socket[IO]): IO[Unit] = {
  //   for {
  //     _ <- IO.println("New client connection")
  //     // 处理认证阶段
  //     authMethod <- negotiateAuth(clientSocket)
  //     _ <- IO.println(s"Negotiated auth method: $authMethod")

  //     // 处理连接请求
  //     connectReq <- readConnectRequest(clientSocket)
  //     _ <- IO.println(s"Connect request: ${connectReq.address}:${connectReq.port}")

  //     // 连接目标服务器
  //     targetSocket <- connectToTarget(connectReq)

  //     // 发送成功响应
  //     _ <- sendConnectResponse(clientSocket, connectReq)

  //     // 启动双向数据转发
  //     _ <- forwardTraffic(clientSocket, targetSocket).start
  //   } yield ()
  // }

  // def negotiateAuth(clientSocket: Socket[IO]): IO[Byte] = {
  //   for {
  //     // 读取认证请求
  //     authReq <- readAuthRequest(clientSocket)

  //     // 验证版本和支持的方法
  //     selectedMethod = if (authReq.version == Socks5.Version &&
  //       authReq.methods.contains(Socks5.MethodNoAuth)) {
  //       Socks5.MethodNoAuth
  //     } else {
  //       Socks5.MethodNotAvailable
  //     }

  //     // 发送认证响应
  //     _ <- sendAuthResponse(clientSocket, selectedMethod)

  //     // 如果不支持任何方法，关闭连接
  //     _ <- if (selectedMethod == Socks5.MethodNotAvailable) {
  //       clientSocket.endOfOutput
  //     } else {
  //       IO.unit
  //     }
  //   } yield selectedMethod
  // }

  // def readAuthRequest(clientSocket: Socket[IO]): IO[AuthRequest] = {
  //   clientSocket.readN(2).flatMap { bytes =>
  //     val version = bytes(0)
  //     val nMethods = bytes(1)

  //     clientSocket.readN(nMethods.toInt).map { methodBytes =>
  //       AuthRequest(version, nMethods, methodBytes.toList)
  //     }
  //   }
  // }

  // def sendAuthResponse(clientSocket: Socket[IO], method: Byte): IO[Unit] = {
  //   val response = Chunk.array(Array(Socks5.Version, method))
  //   clientSocket.write(response)
  // }

  // def readConnectRequest(clientSocket: Socket[IO]): IO[ConnectRequest] = {
  //   for {
  //     // 读取固定头部(4字节)
  //     header <- clientSocket.readN(4)
  //     version = header(0)
  //     cmd = header(1)
  //     rsv = header(2)
  //     addrType = header(3)

  //     // 根据地址类型读取地址
  //     (address, port) <- addrType match {
  //       case Socks5.AddrTypeIPv4 =>
  //         for {
  //           ipBytes <- clientSocket.readN(4)
  //           portBytes <- clientSocket.readN(2)
  //           ip = s"${ipBytes(0) & 0xFF}.${ipBytes(1) & 0xFF}.${ipBytes(2) & 0xFF}.${ipBytes(3) & 0xFF}"
  //           port = ((portBytes(0) & 0xFF) << 8) | (portBytes(1) & 0xFF)
  //         } yield (ip, port)

  //       case Socks5.AddrTypeDomain =>
  //         for {
  //           lenByte <- clientSocket.readN(1)
  //           len = lenByte(0) & 0xFF
  //           domainBytes <- clientSocket.readN(len)
  //           domain = new String(domainBytes.toArray)
  //           portBytes <- clientSocket.readN(2)
  //           port = ((portBytes(0) & 0xFF) << 8) | (portBytes(1) & 0xFF)
  //         } yield (domain, port)

  //       case _ =>
  //         IO.raiseError(new Exception(s"Unsupported address type: $addrType"))
  //     }
  //   } yield ConnectRequest(version, cmd, rsv, addrType, address, port)
  // }

  // def connectToTarget(req: ConnectRequest): IO[Socket[IO]] = {
  //   Socket[IO].connect(new InetSocketAddress(req.address, req.port))
  // }

  // def sendConnectResponse(clientSocket: Socket[IO], req: ConnectRequest): IO[Unit] = {
  //   val response = Chunk.array {
  //     req.addrType match {
  //       case Socks5.AddrTypeIPv4 =>
  //         // 成功响应 + 回显客户端地址和端口
  //         Array(
  //           Socks5.Version,
  //           Socks5.ReplySuccess,
  //           0x00, // RSV
  //           Socks5.AddrTypeIPv4,
  //           0x00, 0x00, 0x00, 0x00, // 回显地址(这里用0.0.0.0)
  //           0x00, 0x00 // 回显端口(这里用0)
  //         )

  //       case Socks5.AddrTypeDomain =>
  //         // 成功响应 + 回显域名地址和端口
  //         val domainBytes = req.address.getBytes
  //         val lenByte = domainBytes.length.toByte

  //         Array(Socks5.Version, Socks5.ReplySuccess, 0x00, Socks5.AddrTypeDomain) ++
  //           Array(lenByte) ++ domainBytes ++
  //           Array(((req.port >> 8) & 0xFF).toByte, (req.port & 0xFF).toByte)
  //     }
  //   }

  //   clientSocket.write(response)
  // }

  // def forwardTraffic(client: Socket[IO], target: Socket[IO]): IO[Unit] = {
  //   val clientToTarget = client.reads
  //     .through(fs2.io.net.Socket.writes(target))
  //     .onFinalize(IO.println("Client to target stream closed"))

  //   val targetToClient = target.reads
  //     .through(fs2.io.net.Socket.writes(client))
  //     .onFinalize(IO.println("Target to client stream closed"))

  //   // 双向转发，任一方向结束则关闭整个连接
  //   (clientToTarget race targetToClient)
  //     .compile
  //     .drain
  //     .onFinalize {
  //       IO.parSequence_(
  //         client.endOfOutput,
  //         target.endOfOutput
  //       )
  //     }
  // }

  // import java.net.InetSocketAddress
  // import scala.concurrent.duration.FiniteDuration

  // trait Socks5Api[F[_]] {
  //   // 协商阶段：处理客户端发送的初始协商请求
  //   def handleNegotiation(clientMethods: List[AuthMethod]): F[SelectedAuthMethod]

  //   // 认证阶段：根据所选方法处理认证
  //   def handleAuthentication(method: AuthMethod, payload: Array[Byte]): F[AuthResult]

  //   // 请求阶段：处理客户端的连接请求
  //   def handleRequest(request: Socks5Request): F[Socks5Response]

  //   // 建立TCP连接（用于CONNECT命令）
  //   def establishTcpConnection(address: InetSocketAddress, timeout: FiniteDuration): F[Unit]

  //   // 绑定本地端口（用于BIND命令）
  //   def bindLocalPort(port: Int): F[InetSocketAddress]

  //   // 建立UDP关联（用于UDP ASSOCIATE命令）
  //   def establishUdpAssociation(clientAddress: InetSocketAddress): F[InetSocketAddress]

  //   // 数据传输：转发客户端到目标的数据
  //   def forwardToTarget(clientData: Array[Byte], target: InetSocketAddress): F[Array[Byte]]

  //   // 数据传输：转发目标到客户端的数据
  //   def forwardToClient(targetData: Array[Byte], client: InetSocketAddress): F[Unit]

  //   // 关闭连接
  //   def closeConnection(client: InetSocketAddress): F[Unit]
  // }

  // sealed trait Socks5ResponseStatus

  // object Socks5ResponseStatus {
  //   case object Succeeded extends Socks5ResponseStatus

  //   case object GeneralSocksServerFailure extends Socks5ResponseStatus

  //   case object ConnectionNotAllowedByRuleset extends Socks5ResponseStatus

  //   case object NetworkUnreachable extends Socks5ResponseStatus

  //   case object HostUnreachable extends Socks5ResponseStatus

  //   case object ConnectionRefused extends Socks5ResponseStatus

  //   case object TtlExpired extends Socks5ResponseStatus

  //   case object CommandNotSupported extends Socks5ResponseStatus

  //   case object AddressTypeNotSupported extends Socks5ResponseStatus
  // }

  // case class Socks5Response(
  //                            status: Socks5ResponseStatus,
  //                            boundAddress: String,
  //                            boundPort: Int
  //                          )

}
