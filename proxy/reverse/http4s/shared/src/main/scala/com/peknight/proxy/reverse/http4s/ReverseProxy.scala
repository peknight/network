package com.peknight.proxy.reverse.http4s

import cats.Monad
import cats.effect.{Concurrent, Resource}
import cats.syntax.applicative.*
import cats.syntax.eq.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.monadError.*
import cats.syntax.option.*
import com.comcast.ip4s.{Ipv4Address, Ipv6Address, Port}
import com.peknight.fs2.pipe.scanS
import com.peknight.http4s.uri.host.fromString
import com.peknight.http4s.uri.scheme.{ws, wss}
import fs2.{Pipe, Stream}
import org.http4s.*
import org.http4s.Method.HEAD
import org.http4s.client.Client
import org.http4s.client.websocket.{WSClient, WSConnection, WSFrame, WSRequest}
import org.http4s.headers.*
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.typelevel.ci.CIStringSyntax

trait ReverseProxy:

  def uri[F[_]: Concurrent](
                             clientR: Resource[F, Client[F]],
                             wsClientR: Resource[F, WSClient[F]],
                             webSocketBuilder: WebSocketBuilder[F],
                             scheme: Option[Uri.Scheme] = None,
                             wsScheme: Option[Uri.Scheme] = None,
                             forwardedBy: Option[Forwarded.Node] = None,
                             overwriteReferrer: Boolean = false
                           )(f: PartialFunction[Request[F], Uri])(g: PartialFunction[Request[F], Uri]): HttpRoutes[F] =
    apply[F](clientR, wsClientR, webSocketBuilder, req => f.isDefinedAt(req), scheme, wsScheme, forwardedBy,
      req => f(req).pure[F],
      (uri, req) => uri.host.map(host => Host(host.value, uri.authority.flatMap(_.port))).pure[F],
      (uri, req) =>
        if overwriteReferrer then
          req.headers.get[Referer].mapUri(req)(f)(_.uri)((referrer, uri) => referrer.copy(uri = uri)).pure[F]
        else req.headers.get[Referer].pure[F],
      req => req.pure[F],
      (req, resp) => resp.headers.get[`Content-Location`].mapUri(req)(g)(_.uri)((location, uri) => location.copy(uri = uri)).pure[F],
      (req, resp) => resp.headers.get[Location].mapUri(req)(g)(_.uri)((location, uri) => location.copy(uri = uri)).pure[F],
      (req, resp) => resp.pure[F]
    )

  def apply[F[_]: Concurrent](
                               clientR: Resource[F, Client[F]],
                               wsClientR: Resource[F, WSClient[F]],
                               webSocketBuilder: WebSocketBuilder[F],
                               isDefinedAt: Request[F] => Boolean,
                               scheme: Option[Uri.Scheme],
                               wsScheme: Option[Uri.Scheme],
                               forwardedBy: Option[Forwarded.Node],
                               uriF: Request[F] => F[Uri],
                               hostF: (Uri, Request[F]) => F[Option[Host]],
                               referrerF: (Uri, Request[F]) => F[Option[Referer]],
                               requestF: Request[F] => F[Request[F]],
                               contentLocationF: (Request[F], Response[F]) => F[Option[`Content-Location`]],
                               locationF: (Request[F], Response[F]) => F[Option[Location]],
                               responseF: (Request[F], Response[F]) => F[Response[F]]
                             ): HttpRoutes[F] = HttpRoutes.of[F] {
    case req if isDefinedAt(req) =>
      for
        request <- handleRequest(req, scheme, forwardedBy, uriF, hostF, referrerF, requestF)
        response <-
          if req.headers.get[Upgrade].exists(_.values.exists(_.name === ci"websocket")) then
            val scheme = wsScheme.orElse(request.uri.scheme.flatMap {
              case scheme if scheme === Uri.Scheme.http => ws.some
              case scheme if scheme === Uri.Scheme.https => wss.some
              case _ => None
            }).getOrElse(ws)
            val headers = request
              .removeHeader[Connection]
              .removeHeader[Host]
              .removeHeader[Upgrade]
              .removeHeader[`Sec-WebSocket-Key`]
              .removeHeader[`Sec-WebSocket-Version`]
              .removeHeader(ci"Sec-WebSocket-Extensions")
              .headers
            val wsRequest = WSRequest(request.uri.copy(scheme = Some(scheme)), headers, request.method)
            wsClientR.flatMap(_.connect(wsRequest)).allocated.flatMap{ (connection, release) =>
              for
                resp <- webSocketBuilder.build(webSocketFramePipe(connection))
                response <- handleResponse(req, resp, release, contentLocationF, locationF, responseF)
              yield
                response
            }
          else
            clientR.flatMap(_.run(request)).allocated.flatMap((resp, release) =>
              handleResponse(req, resp, release, contentLocationF, locationF, responseF)
            )
      yield
        response
  }

  private def handleRequest[F[_]: Monad](req: Request[F],
                                         schemeOption: Option[Uri.Scheme],
                                         forwardedBy: Option[Forwarded.Node],
                                         uriF: Request[F] => F[Uri],
                                         hostF: (Uri, Request[F]) => F[Option[Host]],
                                         referrerF: (Uri, Request[F]) => F[Option[Referer]],
                                         requestF: Request[F] => F[Request[F]]): F[Request[F]] =
    for
      uri <- uriF(req)
      host <- hostF(uri, req)
      referrer <- referrerF(uri, req)
      scheme = req.uri.scheme.orElse(schemeOption)
      forwardedElem = forwardedElement(req, forwardedBy, scheme)
      request <- requestF(req.withUri(uri)
        .removeHeader[Host].putHeaders(host)
        .removeHeader[Referer].putHeaders(referrer)
        .removeHeader[Forwarded].putHeaders(req.headers.get[Forwarded]
          .map(forwarded => forwarded.copy(values = forwarded.values.append(forwardedElem)))
          .getOrElse(Forwarded(forwardedElem)))
        .removeHeader[`X-Forwarded-For`].putHeaders(req.headers.get[`X-Forwarded-For`]
          .map(xForwardedFor => xForwardedFor.copy(values = xForwardedFor.values.append(req.remoteAddr)))
          .getOrElse(`X-Forwarded-For`(req.remoteAddr)))
        .removeHeader[`X-Forwarded-Proto`].putHeaders(scheme.map(`X-Forwarded-Proto`.apply)
          .orElse(req.headers.get[`X-Forwarded-Proto`]))
        .removeHeader[`X-Forwarded-Host`].putHeaders(req.headers.get[`X-Forwarded-Host`]
          .orElse(req.headers.get[Host].map(host => `X-Forwarded-Host`(host.host, host.port))))
        .removeHeader[`X-Forwarded-Port`].putHeaders(req.headers.get[`X-Forwarded-Port`]
          .orElse(req.headers.get[Host].flatMap(_.port)
            .orElse(req.uri.port)
            .flatMap(Port.fromInt)
            .map(`X-Forwarded-Port`.apply)
          )
        )
        .removeHeader[`X-Real-IP`].putHeaders(req.headers.get[`X-Real-IP`].orElse(req.remoteAddr.map(`X-Real-IP`.apply)))
      )
    yield
      request

  private def forwardedElement[F[_]](request: Request[F],
                                     forwardedBy: Option[Forwarded.Node],
                                     forwardedProto: Option[Uri.Scheme]): Forwarded.Element =
    val forwardedHost =
      for
        headerHost <- request.headers.get[Host]
        uriHost <- fromString(headerHost.host)
        forwardedHost <- headerHost.port match
          case Some(headerPort) => Forwarded.Host.fromHostAndPort(uriHost, headerPort).toOption
          case None => Forwarded.Host.ofHost(uriHost).some
      yield
        forwardedHost
    val forwardedFor =
      request.remoteAddr
        .map {
          case address: Ipv4Address => Forwarded.Node(Forwarded.Node.Name.Ipv4(address))
          case address: Ipv6Address => Forwarded.Node(Forwarded.Node.Name.Ipv6(address))
        }
        .orElse(request.headers.get[Forwarded].flatMap(_.values.last.maybeBy))
        .getOrElse(Forwarded.Node(Forwarded.Node.Name.Unknown))
    val element = Forwarded.Element.fromFor(forwardedFor)
    val withByElement = forwardedBy.fold(element)(element.withBy)
    val withHostElement = forwardedHost.fold(withByElement)(withByElement.withHost)
    forwardedProto.fold(withHostElement)(withHostElement.withProto)

  private def handleResponse[F[_]: Concurrent](
                                                req: Request[F],
                                                resp: Response[F], release: F[Unit],
                                                contentLocationF: (Request[F], Response[F]) => F[Option[`Content-Location`]],
                                                locationF: (Request[F], Response[F]) => F[Option[Location]],
                                                responseF: (Request[F], Response[F]) => F[Response[F]]): F[Response[F]] =
    for
      contentLocation <- contentLocationF(req, resp)
      location <- locationF(req, resp)
      given CanEqual[Method, Method] = CanEqual.derived
      given CanEqual[Entity[F], Entity[F]] = CanEqual.derived
      resp <- (req.method, resp.entity) match
        case (HEAD, _) => release.as(resp.withEntity[F](Entity.Empty))
        case (_, Entity.Default(body, length)) => resp.withEntity(Entity.Default(body.onFinalize(release), length)).pure[F]
        case _ => release.as(resp)
      response <- responseF(req, resp
        .removeHeader[`Content-Location`]
        .putHeaders(contentLocation)
        .removeHeader[Location]
        .putHeaders(location)
      )
    yield
      response

  private def webSocketFramePipe[F[_]: Concurrent](connection: WSConnection[F]): Pipe[F, WebSocketFrame, WebSocketFrame] =
    in => Stream(
      in.through(scanS[F, WebSocketFrame, WebSocketFrame, WSFrame, Boolean](true) {
        case (last, frame: WebSocketFrame.Close) =>
          println(s"WebSocket|send|close|$last|${frame.data.toHex}|${frame.last}")
          (frame.last, WSFrame.Close(frame.closeCode, ""))
        case (last, frame: WebSocketFrame.Ping) =>
          println(s"WebSocket|send|ping|$last|${frame.data.toHex}|${frame.last}")
          (frame.last, WSFrame.Ping(frame.data))
        case (last, frame: WebSocketFrame.Pong) =>
          println(s"WebSocket|send|pong|$last|${frame.data.toHex}|${frame.last}")
          (frame.last, WSFrame.Pong(frame.data))
        case (last, frame: WebSocketFrame.Text) =>
          println(s"WebSocket|send|text|$last|${frame.data.toHex}|${frame.last}|${frame.str}")
          (frame.last, WSFrame.Text(frame.str, frame.last))
        case (last, frame: WebSocketFrame.Binary) =>
          println(s"WebSocket|send|binary|$last|${frame.data.toHex}|${frame.last}")
          (true, WSFrame.Binary(frame.data, frame.last))
        case (true, frame) =>
          println(s"WebSocket|send|${frame.getClass.getSimpleName}|true|${frame.data.toHex}|${frame.last}")
          (true, WSFrame.Binary(frame.data, frame.last))
        case (false, frame) => frame.data.decodeUtf8 match
          case Right(data) =>
            println(s"WebSocket|send|${frame.getClass.getSimpleName}|false|${frame.data.toHex}|${frame.last}|text|$data")
            (frame.last, WSFrame.Text(data, frame.last))
          case _ =>
            println(s"WebSocket|send|${frame.getClass.getSimpleName}|false|${frame.data.toHex}|${frame.last}|binary")
            (true, WSFrame.Binary(frame.data, frame.last))
      }).through(connection.sendPipe).map(_ => none[WebSocketFrame]),
      connection.receiveStream.evalMap {
        case WSFrame.Close(statusCode, reason) =>
          println(s"WebSocket|receive|close|$statusCode|$reason")
          WebSocketFrame.Close(statusCode).pure[F].rethrow
        case WSFrame.Ping(data) =>
          println(s"WebSocket|receive|ping|${data.toHex}")
          WebSocketFrame.Ping(data).pure[F]
        case WSFrame.Pong(data) =>
          println(s"WebSocket|receive|pong|${data.toHex}")
          WebSocketFrame.Pong(data).pure[F]
        case WSFrame.Text(data, last) =>
          println(s"WebSocket|receive|text|$data|$last")
          WebSocketFrame.Text(data, last).pure[F]
        case WSFrame.Binary(data, last) =>
          println(s"WebSocket|receive|binary|${data.toHex}|$last")
          WebSocketFrame.Binary(data, last).pure[F]
      }.map(_.some)
    ).parJoin(2).collect { case Some(frame) => frame }

  extension [A] (option: Option[A])
    private def mapUri[F[_]](request: Request[F])(f: PartialFunction[Request[F], Uri])(get: A => Uri)(update: (A, Uri) => A): Option[A] =
      option.map { a =>
        val origin = get(a)
        val req = request.withUri(origin).removeHeader[Host]
        if f.isDefinedAt(req) then update(a, f(req)) else a
      }
  end extension
end ReverseProxy
object ReverseProxy extends ReverseProxy
