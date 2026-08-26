package com.peknight.socks5.state

import com.peknight.error.std.WrongClassTag
import com.peknight.fs2.pull.state.BytePullStateErrorDsl
import com.peknight.socks5.state.State.Terminated
import scodec.bits.ByteVector

import scala.reflect.ClassTag

/**
 * SOCKS5 状态机专用的 [[BytePullStateErrorDsl]]：
 * 固定 `S = State`、`E = Terminated`，底层 `Throwable` 统一通过 `State.error` 提升。
 */
trait PullStateDsl extends BytePullStateErrorDsl[State, Terminated]:

  def error(state: State, throwable: Throwable): Terminated = state.error(throwable)

  override def setS[F[_]](s: State): AUX[F, Unit] =
    s match
      case terminated: Terminated => liftL(terminated)
      case state => super.setS(state)

  def typed[F[_], A: ClassTag](any: Any): AUX[F, A] =
    super.typed[F, Any, A](any)((s, a) => s.error(WrongClassTag[A](a)))

  def typedS[F[_], A: ClassTag]: AUX[F, A] =
    super.typedS(s => s.error(WrongClassTag[A](s)))

  extension [F[_]](state: AUX[F, State])
    def outputS(f: State => ByteVector): AUX[F, State] =
      state.attempt.outputE(either => f(either.fold[State](identity, identity)))
  end extension
end PullStateDsl
