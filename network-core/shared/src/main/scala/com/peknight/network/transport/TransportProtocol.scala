package com.peknight.network.transport

import cats.{Applicative, Show}
import com.peknight.codec.Codec
import com.peknight.codec.config.Config
import com.peknight.codec.cursor.Cursor
import com.peknight.codec.derivation.EnumCodecDerivation
import com.peknight.codec.sum.StringType

enum TransportProtocol:
  case TCP, UDP
end TransportProtocol
object TransportProtocol:
  given stringCodecTransportProtocol[F[_]](using Config)(using Applicative[F]): Codec[F, String, String, TransportProtocol] =
    EnumCodecDerivation.unsafeDerivedStringCodecEnum[F, TransportProtocol]

  given codecTransportProtocolS[F[_], S](using Config)(using Applicative[F], StringType[S], Show[S])
  : Codec[F, S, Cursor[S], TransportProtocol] =
    Codec.codecS[F, S, TransportProtocol]
end TransportProtocol
