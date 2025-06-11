package com.peknight.proxy.reverse.http4s

import cats.effect.{Concurrent, Resource}
import com.comcast.ip4s.{Host, Port}
import com.peknight.http4s.ext.syntax.uri.withAuthority
import org.http4s.client.Client
import org.http4s.client.websocket.WSClient
import org.http4s.headers.Forwarded
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.{HttpRoutes, Uri}

trait HostReverseProxy:
  def host: Host
  def proxyHost: Host
  def port: Option[Port] = None
  def proxyPort: Option[Port] = None
  def scheme: Option[Uri.Scheme] = None
  def proxyScheme: Option[Uri.Scheme] = None
  def overwriteReferrer: Boolean = false
  def requestUriF: PartialFunction[Uri, Uri] =
    uriF(proxyHost, host, port, scheme)
  def responseUriF: PartialFunction[Uri, Uri] =
    uriF(host, proxyHost, proxyPort, proxyScheme)
  def uriF(matcher: Host, host: Host, port: Option[Port], scheme: Option[Uri.Scheme]): PartialFunction[Uri, Uri] =
    case uri if uri.host.exists(_.value.contains(matcher.toString)) =>
      uri.withAuthority(org.http4s.Uri.Host.fromIp4sHost(host), port, replacePort = true)
        .copy(scheme = scheme.orElse(uri.scheme))
  def apply[F[_]: Concurrent](
                               clientR: Resource[F, Client[F]],
                               wsClientR: Resource[F, WSClient[F]],
                               webSocketBuilder: WebSocketBuilder[F],
                               forwardedBy: Option[Forwarded.Node] = None
                             ): HttpRoutes[F] =
    ReverseProxy.uri[F](clientR, wsClientR, webSocketBuilder, proxyScheme, forwardedBy, overwriteReferrer)(requestUriF)(responseUriF)
end HostReverseProxy
