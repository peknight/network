package com.peknight.socks5.api.udp

import com.peknight.socks5.{Request, Response}
import com.peknight.socks5.api.ConnectionContext
import fs2.Pipe

/** UDP ASSOCIATE command sub-API (RFC 1928 §4.3, §4.4, §5).
  *
  * The server establishes a UDP relay end-point and uses the TCP connection
  * as a control channel:
  *
  *   1. Reply to the client with the relay socket's address/port.
  *   2. Relay UDP datagrams (with SOCKS5 UDP request/response headers) between
  *      the client and arbitrary targets.
  *   3. Keep the TCP connection open; when it closes, tear down the UDP relay.
  *
  * The `relay` pipe is wired to the TCP control connection:
  * {{{client.reads.through(relay).through(client.writes)}}}
  *
  * Per RFC, any bytes received on the TCP control connection after the reply
  * are ignored; the pipe should launch the UDP relay concurrently and terminate
  * (tearing the relay down) when its input stream ends.
  *
  * @tparam F effect type
  * @tparam C UDP relay channel type (e.g. a `DatagramSocket`-like handle)
  */
trait UdpAssociateApi[F[_], C]:

  def udpAssociate(request: Request, ctx: ConnectionContext): F[UdpAssociateApi.UdpAssociateResult[F, C]]

end UdpAssociateApi

object UdpAssociateApi:

  /** Result of a UDP ASSOCIATE request.
    *
    * @param response reply containing the address/port of the UDP relay end-point
    * @param channel  handle for the UDP relay (lifecycle / observability)
    * @param relay    control-connection pipe. Its input is the TCP stream from
    *                 the client (ignored per spec); its output is written back
    *                 to the TCP socket (normally empty). The pipe owns the
    *                 concurrent UDP relay and tears it down when the input ends.
    */
  case class UdpAssociateResult[F[_], C](
    response: Response,
    channel: C,
    relay: Pipe[F, Byte, Byte]
  )

end UdpAssociateApi
