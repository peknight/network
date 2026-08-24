package com.peknight.socks5.server.state

import com.peknight.error.std.WrongClassTag
import com.peknight.fs2.pull.state.BytePullStateDsl
import com.peknight.socks5.server.state.State.Terminated
import fs2.Stream.ToPull
import fs2.{Chunk, Pull, Stream}
import scodec.bits.ByteVector

import java.nio.charset.Charset
import scala.reflect.ClassTag

/**
 * SOCKS5 服务端状态机专用的 [[BytePullStateDsl]]：固定 `S = State`、`E = Terminated`，
 * 并把 `Throwable` 统一通过 `State.error` 提升为 `Terminated`，EOF 直接接收 `=> Throwable`。
 */
object Socks5PullState extends BytePullStateDsl[State, Terminated]:

  override def setS[F[_]](s: State): PS[F, Unit] =
    s match
      case terminated: Terminated => liftL(terminated)
      case state => super.setS(state)

  def liftPET[F[_], A](pull: Pull[F, Byte, Either[Throwable, A]]): PS[F, A] =
    super.liftPET(pull)(_.error(_))
  def liftPLT[F[_], A](pull: Pull[F, Byte, Throwable]): PS[F, A] =
    super.liftPLT(pull)(_.error(_))
  def liftFET[F[_], A](f: F[Either[Throwable, A]]): PS[F, A] =
    super.liftFET(f)(_.error(_))
  def liftFLT[F[_], A](f: F[Throwable]): PS[F, A] =
    super.liftFLT(f)(_.error(_))
  def liftET[F[_], A](either: Either[Throwable, A]): PS[F, A] =
    super.liftET(either)(_.error(_))
  def liftT[F[_], A](t: Throwable): PS[F, A] =
    super.liftT(t)(_.error(_))

  def pull[F[_], A](f: ToPull[F, Byte] => Pull[F, Byte, Option[(A, Stream[F, Byte])]])(eof: => Throwable)
  : PS[F, A] =
    super.pull(f)(_.error(eof))

  def map[F[_], I, A](f: ToPull[F, Byte] => Pull[F, Byte, Option[(I, Stream[F, Byte])]])
                     (g: I => A)(eof: => Throwable): PS[F, A] =
    super.map(f)(g)(_.error(eof))

  def map1[F[_], A](f: Byte => A)(eof: => Throwable): PS[F, A] =
    super.map1(f)(_.error(eof))

  def mapChunk[F[_], A](f: ToPull[F, Byte] => Pull[F, Byte, Option[(Chunk[Byte], Stream[F, Byte])]])
                       (g: Chunk[Byte] => A)(eof: => Throwable): PS[F, A] =
    super.mapChunk(f)(g)(_.error(eof))

  def parse[F[_], I, A](f: ToPull[F, Byte] => Pull[F, Byte, Option[(I, Stream[F, Byte])]])
                       (g: I => Either[Throwable, A])(eof: => Throwable): PS[F, A] =
    super.parse(f)(g)(_.error(_))(_.error(eof))

  def parse1[F[_], A](f: Byte => Either[Throwable, A])(eof: => Throwable): PS[F, A] =
    super.parse1(f)(_.error(_))(_.error(eof))

  def parseChunk[F[_], A](f: ToPull[F, Byte] => Pull[F, Byte, Option[(Chunk[Byte], Stream[F, Byte])]])
                         (g: Chunk[Byte] => Either[Throwable, A])(eof: => Throwable): PS[F, A] =
    super.parseChunk(f)(g)(_.error(_))(_.error(eof))

  def readSizedBytes[F[_]](eof: => Throwable): PS[F, Chunk[Byte]] =
    super.readSizedBytes(_.error(eof))

  def mapSizedBytes[F[_], A](f: Chunk[Byte] => A)(eof: => Throwable): PS[F, A] =
    super.mapSizedBytes(f)(_.error(eof))

  def parseSizedBytes[F[_], A](f: Chunk[Byte] => Either[Throwable, A])(eof: => Throwable): PS[F, A] =
    super.parseSizedBytes(f)(_.error(_))(_.error(eof))

  def readSizedString[F[_]](eof: => Throwable)(using Charset): PS[F, String] =
    super.readSizedString(_.error(_))(_.error(eof))

  def mapSizedString[F[_], A](f: String => A)(eof: => Throwable)(using Charset): PS[F, A] =
    super.mapSizedString(f)(_.error(_))(_.error(eof))

  def parseSizedString[F[_], A](f: String => Either[Throwable, A])(eof: => Throwable)(using Charset): PS[F, A] =
    super.parseSizedString(f)(_.error(_))(_.error(eof))

  def typed[F[_], A: ClassTag](any: Any): PS[F, A] =
    super.typed[F, Any, A](any)((s, a) => s.error(WrongClassTag[A](a)))

  def typedS[F[_], A: ClassTag]: PS[F, A] =
    super.typedS(s => s.error(WrongClassTag[A](s)))

  extension [F[_], A](state: PS[F, A])
    /** 无参版本：把底层 throwable 通过当前 State 提升为 Terminated。 */
    def attempt: PS[F, A] = super[BytePullStateDsl].attempt(state)(_.error(_))
  end extension

  extension [F[_]](state: PS[F, State])
    def outputS(f: State => ByteVector): PS[F, State] =
      state.attempt.outputE(either => f(either.fold[State](identity, identity)))
  end extension
end Socks5PullState
