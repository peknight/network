package com.peknight.socks5.server

import com.peknight.error.Error
import com.peknight.socks5.Reply.{AddressTypeNotSupported, CommandNotSupported, Failed, GeneralSocksServerFailure}
import com.peknight.socks5.Response

package object error:
  def toFailed[E](error: E): Failed =
    Error(error) match
      case _: UnsupportedCommand => CommandNotSupported
      case _: UnsupportedAddressType => AddressTypeNotSupported
      case _ => GeneralSocksServerFailure
  def toResponse[E](error: E): Response =
    Response.failed(toFailed(error))
end error