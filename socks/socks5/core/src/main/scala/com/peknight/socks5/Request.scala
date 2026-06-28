package com.peknight.socks5

import com.comcast.ip4s.{Host, Port}
import com.peknight.socks5.Command

case class Request(command: Command, address: Host, port: Port)
