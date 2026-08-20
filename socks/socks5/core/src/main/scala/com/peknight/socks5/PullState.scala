package com.peknight.socks5

import cats.data.StateT
import cats.syntax.either.*
import com.peknight.fs2.pull.state.BytePullState
import com.peknight.socks5.State.Terminated
import fs2.Stream.ToPull
import fs2.{Chunk, Pull, RaiseThrowable, Stream}
import scodec.bits.ByteVector

import java.io.EOFException
import java.nio.charset.Charset

object PullState:
  def apply[F[_], A](f: (State, Stream[F, Byte]) => Pull[F, Byte, ((State, Stream[F, Byte]), A)])
  : PullState[F, A] =
    StateT(f.tupled)

  def pure[F[_], A](a: A): PullState[F, A] = StateT.pure(a)

  def unit[F[_]]: PullState[F, Unit] = pure[F, Unit](())

  def liftP[F[_], A](f: Pull[F, Byte, A]): PullState[F, A] = StateT.liftF(f)

  def liftF[F[_], A](f: F[A]): PullState[F, A] = liftP[F, A](Pull.eval(f))

  def raiseError[F[_]: RaiseThrowable, A](e: Throwable): PullState[F, A] = liftP(Pull.raiseError(e))

  def liftE[F[_]: RaiseThrowable, A](either: Either[Throwable, A]): PullState[F, A] =
    BytePullState.liftE[F, Byte, State, A](either)

  def output[F[_]](chunk: Chunk[Byte]): PullState[F, Unit] = BytePullState.output[F, Byte, State](chunk)

  def output[F[_]](os: Byte*): PullState[F, Unit] = BytePullState.output[F, Byte, State](os*)

  def output[F[_]](bytes: ByteVector): PullState[F, Unit] =
    BytePullState.output[F, Byte, State](Chunk.byteVector(bytes))

  def output1[F[_]](o: Byte): PullState[F, Unit] = BytePullState.output1[F, Byte, State](o)

  def pull[F[_]: RaiseThrowable, A](f: ToPull[F, Byte] => Pull[F, Byte, Option[(A, Stream[F, Byte])]])
                                   (eof: => Throwable = new EOFException()): PullState[F, A] =
    BytePullState.pull[F, Byte, State, A](f)(eof)

  def map[F[_]: RaiseThrowable, I, A](f: ToPull[F, Byte] => Pull[F, Byte, Option[(I, Stream[F, Byte])]])
                                     (g: I => A)(eof: => Throwable = new EOFException()): PullState[F, A] =
    BytePullState.map[F, I, Byte, State, A](f)(g)(eof)

  def map1[F[_]: RaiseThrowable, A](f: Byte => A)(eof: => Throwable = new EOFException()): PullState[F, A] =
    BytePullState.map1[F, Byte, State, A](f)(eof)

  def mapChunk[F[_]: RaiseThrowable, A](f: ToPull[F, Byte] => Pull[F, Byte, Option[(Chunk[Byte], Stream[F, Byte])]])
                                       (g: Chunk[Byte] => A)(eof: => Throwable = new EOFException()): PullState[F, A] =
    BytePullState.mapChunk[F, Byte, State, A](f)(g)(eof)

  def parse[F[_]: RaiseThrowable, I, A](f: ToPull[F, Byte] => Pull[F, Byte, Option[(I, Stream[F, Byte])]])
                                       (g: I => Either[Throwable, A])(eof: => Throwable = new EOFException())
  : PullState[F, A] =
    BytePullState.parse[F, I, Byte, State, A](f)(g)(eof)

  def parse1[F[_]: RaiseThrowable, A](f: Byte => Either[Throwable, A])(eof: => Throwable = new EOFException())
  : PullState[F, A] =
    BytePullState.parse1[F, Byte, State, A](f)(eof)

  def parseChunk[F[_]: RaiseThrowable, A](f: ToPull[F, Byte] => Pull[F, Byte, Option[(Chunk[Byte], Stream[F, Byte])]])
                                         (g: Chunk[Byte] => Either[Throwable, A])
                                         (eof: => Throwable = new EOFException()): PullState[F, A] =
    BytePullState.parseChunk[F, Byte, State, A](f)(g)(eof)

  def readSizedBytes[F[_]: RaiseThrowable](eof: => Throwable = new EOFException()): PullState[F, Chunk[Byte]] =
    for
      n <- pull[F, Byte](_.uncons1)(eof)
      chunk <- pull[F, Chunk[Byte]](_.unconsN(n))(eof)
    yield
      chunk

  def mapSizedBytes[F[_]: RaiseThrowable, A](f: Chunk[Byte] => A)(eof: => Throwable = new EOFException())
  : PullState[F, A] =
    readSizedBytes[F](eof).map(f)

  def parseSizedBytes[F[_]: RaiseThrowable, A](f: Chunk[Byte] => Either[Throwable, A])
                                              (eof: => Throwable = new EOFException()): PullState[F, A] =
    for
      chunk <- readSizedBytes[F](eof)
      value <- liftE[F, A](f(chunk))
    yield
      value

  def readSizedString[F[_]](eof: => Throwable = new EOFException())
                           (using Charset)(using RaiseThrowable[F]): PullState[F, String] =
    parseSizedBytes[F, String](_.toByteVector.decodeString)(eof)

  def mapSizedString[F[_], A](f: String => A)(eof: => Throwable = new EOFException())
                             (using Charset)(using RaiseThrowable[F]): PullState[F, A] =
    readSizedString[F](eof).map(f)

  def parseSizedString[F[_], A](f: String => Either[Throwable, A])(eof: => Throwable = new EOFException())
                               (using Charset)(using RaiseThrowable[F])
  : PullState[F, A] =
    for
      value <- readSizedString[F](eof)
      value <- liftE[F, A](f(value))
    yield
      value

  extension [F[_], A] (state: PullState[F, A])
    def attempt: PullState[F, Either[Terminated, A]] =
      apply((s, stream) => state.run((s, stream)).attempt.flatMap {
        case Right((s, value)) => Pull.pure((s, value.asRight[Terminated]))
        case Left(error) =>
          val next = s.error(error)
          Pull.pure(((next, Stream.empty), next.asLeft[A]))
      })
    def outputE(f: Either[Terminated, A] => ByteVector): PullState[F, Unit] =
      attempt.flatMap {
        case Right(a) =>
          PullState.output(f(a.asRight))
        case Left(error) =>
          PullState.output(f(error.asLeft))
      }
  end extension
end PullState
