package com.peknight.socks5.api

import com.peknight.socks5.Request
import com.peknight.socks5.api.bind.BindApi
import com.peknight.socks5.api.connect.ConnectApi
import com.peknight.socks5.api.udp.UdpAssociateApi

/** Sum type representing the outcome of a SOCKS5 request after the API
  * has dispatched it to the correct command handler.
  *
  * The framework pattern-matches on this to know:
  *  - what SOCKS5 reply (or replies) to send
  *  - how to wire the relay pipe(s) after the reply
  */
sealed trait CommandResult[F[_], Cc, Cb, Cu]

object CommandResult:

  /** CONNECT established (or failed to establish) an outbound connection.
    * One reply, then bidirectional relay.
    */
  case class Connected[F[_], Cc, Cb, Cu](
    result: ConnectApi.ConnectResult[F, Cc]
  ) extends CommandResult[F, Cc, Cb, Cu]

  /** BIND opened a listener and is waiting for the inbound connection.
    * First reply immediately; second reply after the target connects.
    */
  case class Bound[F[_], Cc, Cb, Cu](
    result: BindApi.BindResult[F, Cb]
  ) extends CommandResult[F, Cc, Cb, Cu]

  /** UDP ASSOCIATE established a UDP relay.
    * One reply; the TCP connection is held as a control channel.
    */
  case class UdpAssociated[F[_], Cc, Cb, Cu](
    result: UdpAssociateApi.UdpAssociateResult[F, Cu]
  ) extends CommandResult[F, Cc, Cb, Cu]

end CommandResult
