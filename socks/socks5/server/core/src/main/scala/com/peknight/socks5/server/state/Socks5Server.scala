package com.peknight.socks5.server.state

import cats.effect.{Async, Concurrent}
import cats.syntax.all.*
import com.comcast.ip4s.*
import com.peknight.socks5.{Command, Reply, Request, Response}
import com.peknight.socks5.api.{ConnectionContext, Socks5Api}
import fs2.{Pull, Stream}
import fs2.io.net.{Network, Socket}

import java.nio.charset.{Charset, StandardCharsets}

/** SOCKS5 server handler — bridges the protocol codec (State) with fs2 Socket I/O.
  *
  * Drives the three-phase handshake (negotiation → authentication → request)
  * via `runPhase`, then executes the command (CONNECT relay).
  */
object Socks5Server:

  /** Bind to the given address and serve SOCKS5 connections.
    * Each accepted connection is handled concurrently via `parJoinUnbounded`.
    */
  def serve[F[_]](api: Socks5Api[F], address: SocketAddress[Host])(using Async[F])
  : Stream[F, Nothing] =
    val network = Network.forAsync[F]
    Stream.resource(network.bind(address)).flatMap { serverSocket =>
      serverSocket.accept.map { clientSocket =>
        Stream.eval(handle(api, clientSocket)).drain
      }.parJoinUnbounded
    }

  /** Handle a single SOCKS5 client connection through the full protocol lifecycle. */
  def handle[F[_]](api: Socks5Api[F], socket: Socket[F])(using F: Async[F]): F[Unit] =
    given Charset = StandardCharsets.UTF_8
    val ctx = ConnectionContext(socket.address, socket.peerAddress)
    for
      // Phase 1: Method negotiation
      r1 <- runPhase(negotiation[F](methods => api.negotiation(methods, ctx)), socket.reads, socket)
      // Phase 2: Authentication
      r2 <- runPhase(authentication[F](r1.value)(p => api.passwordAuth(p, ctx)), r1.remaining, socket)
      // Phase 3: Request — capture the request via Ref for command dispatch
      reqRef <- F.ref[Option[Request]](None)
      r3 <- runPhase(request[F] { req =>
        reqRef.set(Some(req)) *> api.connect(req, ctx)
      }, r2.remaining, socket)
      // Execute command (relay for CONNECT)
      req <- reqRef.get.map(_.get)
      _ <- executeCommand(req, r3.value, socket).compile.drain
    yield
      ()
  end handle

  /** Result of running a protocol phase: remaining byte stream and parsed value. */
  private case class PhaseResult[F[_], A](remaining: Stream[F, Byte], value: A)

  /** Run a protocol phase: execute the state machine on the given byte stream,
    * write output bytes to the socket, and return the remaining stream with the parsed result.
    *
    * Uses a `Ref` to capture the Pull's terminal value (remaining stream + result),
    * since Pull#stream discards the result.
    */
  private def runPhase[F[_], A](phase: State[F, A], initial: Stream[F, Byte], socket: Socket[F])
                               (using F: Concurrent[F]): F[PhaseResult[F, A]] =
    for
      ref <- F.ref[Either[Throwable, (Stream[F, Byte], A)]](null)
      pull = phase.run(initial).attempt.evalMap(result => ref.set(result))
      _ <- pull.stream.through(socket.writes).compile.drain
      result <- F.flatMap(ref.get) {
        case Right((remaining, value)) => F.pure(PhaseResult(remaining, value))
        case Left(e) => F.raiseError[PhaseResult[F, A]](e)
      }
    yield
      result
  end runPhase

  /** Execute the command from the SOCKS5 request.
    * Currently only CONNECT is implemented (TCP relay).
    */
  private def executeCommand[F[_]](request: Request, response: Response, client: Socket[F])
                                  (using F: Async[F]): Stream[F, Nothing] =
    (request.command, response.reply) match
      case (Command.CONNECT, Reply.Succeeded) =>
        connectRelay(SocketAddress(response.address, response.port), client)
      case _ =>
        Stream.empty.covary[F]

  /** Establish a TCP connection to the target and relay data bidirectionally
    * between the client and target sockets.
    *
    * Halts when either direction completes (connection closed by either side).
    */
  private def connectRelay[F[_]](target: SocketAddress[Host], client: Socket[F])
                                (using F: Async[F]): Stream[F, Nothing] =
    val network = Network.forAsync[F]
    Stream.resource(network.connect(target)).flatMap { targetSocket =>
      client.reads.through(targetSocket.writes)
        .mergeHaltBoth(targetSocket.reads.through(client.writes))
    }

end Socks5Server
