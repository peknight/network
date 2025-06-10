package com.peknight.socks5

import com.comcast.ip4s.{Host, Port}

case class Response(reply: Reply, address: Host, port: Port)
