package com.peknight.socks5.api.connect

import com.peknight.socks5.{Request, Response}
import com.peknight.socks5.api.ConnectionContext
import fs2.Pipe

/** CONNECT command sub-API.
  *
  * Establish an outbound TCP connection to the target and relay bytes bidirectionally.
  *
  * The implementation decides how the connection is established — direct `Socket`,
  * an upstream proxy chain, a tunnel, etc. The type parameter `C` represents the
  * resulting channel handle (e.g. `Socket[F]`, a proxy session, any custom transport).
  *
  * @tparam F effect type
  * @tparam C established channel type (implementation-specific)
  */
trait ConnectApi[F[_], C]:

  /** Handle a CONNECT request.
    *
    * On failure the implementation should return a [[ConnectResult]] whose
    * `response.reply` is an error code — do not raise an exception, so the
    * framework can encode the error reply to the client.
    */
  def connect(request: Request, ctx: ConnectionContext): F[ConnectApi.ConnectResult[F, C]]

end ConnectApi

object ConnectApi:

  /** Result of a CONNECT request.
    *
    * @param response SOCKS5 reply to send to the client (reply code + bound address/port)
    * @param channel  the established outbound channel, exposed for observability/lifecycle
    * @param relay    bidirectional relay pipe. After the response is sent, the framework
    *                 connects it as: {{{client.reads.through(relay).through(client.writes)}}}
    *                 The pipe owns target-side I/O and is responsible for terminating
    *                 when either direction closes.
    */
  case class ConnectResult[F[_], C](
    response: Response,
    channel: C,
    relay: Pipe[F, Byte, Byte]
  )

end ConnectApi
