package com.peknight.socks5

import com.peknight.error.std.WrongClassTag
import com.peknight.fs2.pull.state.BytePullState
import com.peknight.fs2.pull.state.BytePullState.{attempt as pullStateAttempt, output as pullStateOutput, outputE as pullStateOutputE, outputL as pullStateOutputL}
import com.peknight.socks5.State.Terminated
import fs2.Stream.ToPull
import fs2.{Chunk, Pull, Stream}
import scodec.bits.ByteVector

import java.nio.charset.Charset
import scala.reflect.ClassTag

object Socks5PullState:
  def apply[F[_], A](f: (State, Stream[F, Byte]) => Pull[F, Byte, Either[Terminated, ((State, Stream[F, Byte]), A)]])
  : Socks5PullState[F, A] =
    BytePullState[F, State, Terminated, A](f)

  def pure[F[_], A](a: A): Socks5PullState[F, A] = BytePullState.pure[F, State, Terminated, A](a)

  def unit[F[_]]: Socks5PullState[F, Unit] = BytePullState.unit[F, State, Terminated]

  def get[F[_]]: Socks5PullState[F, (State, Stream[F, Byte])] = BytePullState.get[F, State, Terminated]

  def getS[F[_]]: Socks5PullState[F, State] = BytePullState.getS[F, State, Terminated]

  def setS[F[_]](s: State): Socks5PullState[F, Unit] =
    s match
      case terminated: Terminated => liftL[F, Unit](terminated)
      case state => BytePullState.setS[F, State, Terminated](s)

  def liftPE[F[_], A](pull: Pull[F, Byte, Either[Terminated, A]]): Socks5PullState[F, A] =
    BytePullState.liftPE[F, State, Terminated, A](pull)

  def liftP[F[_], A](pull: Pull[F, Byte, A]): Socks5PullState[F, A] =
    BytePullState.liftP[F, State, Terminated, A](pull)

  def liftPL[F[_], A](pull: Pull[F, Byte, Terminated]): Socks5PullState[F, A] =
    BytePullState.liftPL[F, State, Terminated, A](pull)

  def liftFE[F[_], A](f: F[Either[Terminated, A]]): Socks5PullState[F, A] =
    BytePullState.liftFE[F, State, Terminated, A](f)

  def liftF[F[_], A](f: F[A]): Socks5PullState[F, A] =
    BytePullState.liftF[F, State, Terminated, A](f)

  def liftFL[F[_], A](f: F[Terminated]): Socks5PullState[F, A] =
    BytePullState.liftFL[F, State, Terminated, A](f)

  def liftE[F[_], A](either: Either[Terminated, A]): Socks5PullState[F, A] =
    BytePullState.liftE[F, State, Terminated, A](either)

  def liftL[F[_], A](e: Terminated): Socks5PullState[F, A] = BytePullState.liftL[F, State, Terminated, A](e)

  def liftPET[F[_], A](pull: Pull[F, Byte, Either[Throwable, A]]): Socks5PullState[F, A] =
    BytePullState.liftPET[F, State, Terminated, A](pull)(_.error(_))

  def liftPLT[F[_], A](pull: Pull[F, Byte, Throwable]): Socks5PullState[F, A] =
    BytePullState.liftPLT[F, State, Terminated, A](pull)(_.error(_))

  def liftFET[F[_], A](f: F[Either[Throwable, A]]): Socks5PullState[F, A] =
    BytePullState.liftFET[F, State, Terminated, A](f)(_.error(_))

  def liftFLT[F[_], A](f: F[Throwable]): Socks5PullState[F, A] =
    BytePullState.liftFLT[F, State, Terminated, A](f)(_.error(_))

  def liftET[F[_], A](either: Either[Throwable, A]): Socks5PullState[F, A] =
    BytePullState.liftET[F, State, Terminated, A](either)(_.error(_))

  def liftT[F[_], A](t: Throwable): Socks5PullState[F, A] =
    BytePullState.liftT[F, State, Terminated, A](t)(_.error(_))

  def output[F[_]](chunk: Chunk[Byte]): Socks5PullState[F, Unit] =
    BytePullState.output[F, State, Terminated](chunk)

  def output[F[_]](os: Byte*): Socks5PullState[F, Unit] = BytePullState.output[F, State, Terminated](os *)

  def output[F[_]](bytes: ByteVector): Socks5PullState[F, Unit] =
    BytePullState.output[F, State, Terminated](Chunk.byteVector(bytes))

  def output1[F[_]](o: Byte): Socks5PullState[F, Unit] = BytePullState.output1[F, State, Terminated](o)

  def typed[F[_], A: ClassTag](any: Any): Socks5PullState[F, A] =
    BytePullState.typed[F, State, Terminated, Any, A](any)((s, a) => s.error(WrongClassTag[A](a)))

  def typedS[F[_], A: ClassTag]: Socks5PullState[F, A] =
    BytePullState.typedS[F, State, Terminated, A](s => s.error(WrongClassTag[A](s)))

  def pull[F[_], A](f: ToPull[F, Byte] => Pull[F, Byte, Option[(A, Stream[F, Byte])]])(eof: => Throwable)
  : Socks5PullState[F, A] =
    BytePullState.pull[F, State, Terminated, A](f)(_.error(eof))

  def map[F[_], I, A](f: ToPull[F, Byte] => Pull[F, Byte, Option[(I, Stream[F, Byte])]])(g: I => A)
                     (eof: => Throwable): Socks5PullState[F, A] =
    BytePullState.map[F, I, Byte, State, Terminated, A](f)(g)(_.error(eof))

  def map1[F[_], A](f: Byte => A)(eof: => Throwable): Socks5PullState[F, A] =
    BytePullState.map1[F, State, Terminated, A](f)(_.error(eof))

  def mapChunk[F[_], A](f: ToPull[F, Byte] => Pull[F, Byte, Option[(Chunk[Byte], Stream[F, Byte])]])
                       (g: Chunk[Byte] => A)(eof: => Throwable): Socks5PullState[F, A] =
    BytePullState.mapChunk[F, State, Terminated, A](f)(g)(_.error(eof))

  def parse[F[_], I, A](f: ToPull[F, Byte] => Pull[F, Byte, Option[(I, Stream[F, Byte])]])
                       (g: I => Either[Throwable, A])(eof: => Throwable): Socks5PullState[F, A] =
    BytePullState.parse[F, I, Byte, State, Terminated, A](f)(g)(_.error(_))(_.error(eof))

  def parse1[F[_], A](f: Byte => Either[Throwable, A])(eof: => Throwable): Socks5PullState[F, A] =
    BytePullState.parse1[F, State, Terminated, A](f)(_.error(_))(_.error(eof))

  def parseChunk[F[_], A](f: ToPull[F, Byte] => Pull[F, Byte, Option[(Chunk[Byte], Stream[F, Byte])]])
                         (g: Chunk[Byte] => Either[Throwable, A])(eof: => Throwable): Socks5PullState[F, A] =
    BytePullState.parseChunk[F, State, Terminated, A](f)(g)(_.error(_))(_.error(eof))

  def readSizedBytes[F[_]](eof: => Throwable): Socks5PullState[F, Chunk[Byte]] =
    BytePullState.readSizedBytes[F, State, Terminated](_.error(eof))

  def mapSizedBytes[F[_], A](f: Chunk[Byte] => A)(eof: => Throwable)
  : Socks5PullState[F, A] =
    BytePullState.mapSizedBytes[F, State, Terminated, A](f)(_.error(eof))

  def parseSizedBytes[F[_], A](f: Chunk[Byte] => Either[Throwable, A])(eof: => Throwable): Socks5PullState[F, A] =
    BytePullState.parseSizedBytes[F, State, Terminated, A](f)(_.error(_))(_.error(eof))

  def readSizedString[F[_]](eof: => Throwable)(using Charset): Socks5PullState[F, String] =
    BytePullState.readSizedString[F, State, Terminated](_.error(_))(_.error(eof))

  def mapSizedString[F[_], A](f: String => A)(eof: => Throwable)(using Charset): Socks5PullState[F, A] =
    BytePullState.mapSizedString[F, State, Terminated, A](f)(_.error(_))(_.error(eof))

  def parseSizedString[F[_], A](f: String => Either[Throwable, A])(eof: => Throwable)(using Charset): Socks5PullState[F, A] =
    BytePullState.parseSizedString[F, State, Terminated, A](f)(_.error(_))(_.error(eof))

  extension [F[_], A](state: Socks5PullState[F, A])
    def attempt: Socks5PullState[F, A] = state.pullStateAttempt(_.error(_))
    def outputE(f: Either[Terminated, A] => ByteVector): Socks5PullState[F, A] =
      state.pullStateOutputE(f)
    def output(f: A => ByteVector): Socks5PullState[F, A] =
      state.pullStateOutput(f)
    def outputL(f: Terminated => ByteVector): Socks5PullState[F, A] =
      state.pullStateOutputL(f)
  end extension
  extension [F[_]] (state: Socks5PullState[F, State])
    def outputS(f: State => ByteVector): Socks5PullState[F, State] =
      state.attempt.pullStateOutputE(either => f(either.fold[State](identity, identity)))
  end extension
end Socks5PullState
