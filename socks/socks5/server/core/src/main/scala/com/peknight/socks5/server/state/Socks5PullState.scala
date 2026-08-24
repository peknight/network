package com.peknight.socks5.server.state

import com.peknight.error.std.WrongClassTag
import com.peknight.fs2.pull.state.BytePullStateErrorDsl
import com.peknight.socks5.server.state.State.Terminated
import scodec.bits.ByteVector

import scala.reflect.ClassTag

/**
 * SOCKS5 服务端状态机专用的 [[BytePullStateErrorDsl]]：
 * 固定 `S = State`、`E = Terminated`，底层 `Throwable` 统一通过 `State.error` 提升。
 */
object Socks5PullState extends BytePullStateErrorDsl[State, Terminated]:

  def error(state: State, throwable: Throwable): Terminated = state.error(throwable)

  override def setS[F[_]](s: State): PS[F, Unit] =
    s match
      case terminated: Terminated => liftL(terminated)
      case state => super.setS(state)

  def typed[F[_], A: ClassTag](any: Any): PS[F, A] =
    super.typed[F, Any, A](any)((s, a) => s.error(WrongClassTag[A](a)))

  def typedS[F[_], A: ClassTag]: PS[F, A] =
    super.typedS(s => s.error(WrongClassTag[A](s)))

  extension [F[_]](state: PS[F, State])
    def outputS(f: State => ByteVector): PS[F, State] =
      state.attempt.outputE(either => f(either.fold[State](identity, identity)))
  end extension
end Socks5PullState
