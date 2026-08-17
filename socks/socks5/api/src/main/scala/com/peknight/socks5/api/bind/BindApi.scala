package com.peknight.socks5.api.bind

import com.peknight.socks5.{Request, Response}
import com.peknight.socks5.api.ConnectionContext
import fs2.Pipe

/** BIND command sub-API (RFC 1928 §4.2).
  *
  * The server opens a listening socket and waits for an inbound TCP connection
  * from the target. There are **two** SOCKS5 replies:
  *
  *   1. After the listening socket is bound (the "first reply").
  *   2. After the target actually connects to the listener (the "second reply").
  *
  * The implementation controls both the listener lifecycle and the connection
  * acceptance. `C` is the established inbound channel type.
  *
  * @tparam F effect type
  * @tparam C established inbound channel type (e.g. `Socket[F]`)
  */
trait BindApi[F[_], C]:

  /** Handle a BIND request.
    *
    * Returns a [[BindResult]] containing:
    *  - the first reply (bound listener address/port)
    *  - an effect that, when evaluated, waits for the target to connect and
    *    produces the second reply + the established channel + relay pipe
    */
  def bind(request: Request, ctx: ConnectionContext): F[BindApi.BindResult[F, C]]

end BindApi

object BindApi:

  /** Outcome of waiting for the inbound connection.
    *
    * If the target fails to connect (timeout, refused, wrong peer, etc.) the
    * implementation should return a second reply with an error code rather
    * than raising an exception.
    */
  case class BoundConnection[F[_], C](
    secondReply: Response,
    channel: C,
    relay: Pipe[F, Byte, Byte]
  )

  /** Result of a BIND request.
    *
    * @param firstReply SOCKS5 reply sent immediately after the listener is bound
    * @param accept     effect that semantically blocks until the target connects,
    *                   then returns the second reply and the relay.
    *                   The framework runs this after the first reply has been sent.
    */
  case class BindResult[F[_], C](
    firstReply: Response,
    accept: F[BoundConnection[F, C]]
  )

end BindApi
