package com.peknight.socks5

import com.comcast.ip4s.*
import com.peknight.socks5.Reply.{CommandNotSupported, Failed}

case class Response(reply: Reply, address: Host, port: Port)
object Response:
  val errorHost: Ipv4Address = ipv4"0.0.0.0"
  val errorPort: Port = port"0"
  def failed(reply: Failed): Response = Response(reply, errorHost, errorPort)
  val unsupportedCommand: Response = failed(CommandNotSupported)
  def fromState(state: State): Response = failed(Reply.fromState(state))
end Response
