package com.peknight.socks5.server

import com.peknight.error.Error
import com.peknight.socks5.Reply.{AddressTypeNotSupported, CommandNotSupported, Failed, GeneralSocksServerFailure}
import com.peknight.socks5.Response
import com.peknight.socks5.state.State
import com.peknight.socks5.state.State.ErrorState

package object error:
  private[socks5] def toFailed[E](error: E): Failed =
    Error(error) match
      case _: UnsupportedCommand => CommandNotSupported
      case _: UnsupportedAddressType => AddressTypeNotSupported
      case _ => GeneralSocksServerFailure
  private[socks5] def toResponse[E](error: E): Response =
    Response.failed(toFailed(error))

  private[socks5] def toFailed(state: State): Failed =
    state match
      case e: ErrorState => toFailed(e.error)
      case e: State.UnsupportedCommand[?] => CommandNotSupported
      case _ => GeneralSocksServerFailure
  private[socks5] def toResponse(state: State): Response = Response.failed(toFailed(state))
end error