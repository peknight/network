package com.peknight.socks5.error

import com.peknight.socks.error.StreamEmpty

object AddressTypeEmpty extends StreamEmpty:
  override def lowPriorityMessage: Option[String] = Some("address type empty")
end AddressTypeEmpty
