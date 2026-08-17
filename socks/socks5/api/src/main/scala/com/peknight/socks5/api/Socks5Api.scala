package com.peknight.socks5.api

import cats.Functor
import cats.syntax.functor.*
import com.peknight.socks5.auth.Method
import com.peknight.socks5.auth.password.{Status, UsernamePassword}
import com.peknight.socks5.{Command, Request}
import com.peknight.socks5.api.bind.BindApi
import com.peknight.socks5.api.connect.ConnectApi
import com.peknight.socks5.api.udp.UdpAssociateApi

/** SOCKS5 server-side API.
  *
  * Combines the three command sub-APIs (CONNECT, BIND, UDP ASSOCIATE),
  * each with its own channel type so the implementation is not constrained
  * to any particular transport (Socket, proxy chain, custom handle, etc.).
  *
  * The handshake methods ([[negotiation]], [[passwordAuth]]) are shared
  * across all commands. After authentication succeeds, [[request]] dispatches
  * by command and returns a [[CommandResult]] that the framework uses to
  * send the reply/replies and wire up the relay.
  *
  * @tparam Cc channel type for CONNECT
  * @tparam Cb channel type for BIND
  * @tparam Cu channel type for UDP ASSOCIATE
  */
trait Socks5Api[F[_]: Functor, Cc, Cb, Cu]
  extends ConnectApi[F, Cc]
     with BindApi[F, Cb]
     with UdpAssociateApi[F, Cu]:

  /** Method negotiation phase (RFC 1928 §3).
    *
    * Given the client's offered methods, select one. Returning
    * [[com.peknight.socks5.auth.Method.NoAcceptableMethod]] rejects the connection.
    */
  def negotiation(methods: List[Method], ctx: ConnectionContext): F[Method]

  /** Username/password authentication (RFC 1929).
    * Only called if negotiation selected `UsernamePassword`.
    */
  def passwordAuth(password: UsernamePassword, ctx: ConnectionContext): F[Status]

  /** Dispatch a parsed request to the appropriate command handler.
    *
    * The default implementation selects based on [[Request.command]] and
    * wraps the result in the corresponding [[CommandResult]] variant.
    * Override only if you need to intercept all commands (e.g. logging, policy).
    */
  def request(req: Request, ctx: ConnectionContext): F[CommandResult[F, Cc, Cb, Cu]] =
    req.command match
      case Command.CONNECT =>
        connect(req, ctx).map(CommandResult.Connected(_))
      case Command.BIND =>
        bind(req, ctx).map(CommandResult.Bound(_))
      case Command.UDP_ASSOCIATE =>
        udpAssociate(req, ctx).map(CommandResult.UdpAssociated(_))

end Socks5Api
