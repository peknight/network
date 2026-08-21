package com.peknight.socks5

import com.comcast.ip4s.*
import com.peknight.socks5.Reply.{CommandNotSupported, Failed, Succeeded}

case class Response(reply: Reply, address: Host, port: Port)
object Response:
  val defaultHost: Ipv4Address = ipv4"0.0.0.0"
  val defaultPort: Port = port"0"
  def succeeded(address: GenSocketAddress): Response =
    val (host, port) = address match
      case SocketAddress(host, port) => (host, port)
      case UnixSocketAddress(path) => (defaultHost, defaultPort)
    Response(Succeeded, host, port)
  def failed(reply: Failed): Response = Response(reply, defaultHost, defaultPort)
  val unsupportedCommand: Response = failed(CommandNotSupported)
  def fromError[E](error: E): Response = failed(Reply.fromError(error))
  def fromState(state: State): Response = failed(Reply.fromState(state))
end Response
