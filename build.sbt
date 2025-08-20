import com.peknight.build.gav.*
import com.peknight.build.sbt.*

commonSettings

lazy val network = (project in file("."))
  .settings(name := "network")
  .aggregate(
    socks,
    proxy,
  )

lazy val proxy = (project in file("proxy"))
  .settings(name := "proxy")
  .aggregate(
    reverseProxy,
  )

lazy val reverseProxy = (project in file("proxy/reverse"))
  .settings(name := "reverse-proxy")
  .aggregate(
    reverseProxyHttp4s.jvm,
    reverseProxyHttp4s.js,
  )

lazy val reverseProxyHttp4s = (crossProject(JVMPlatform, JSPlatform) in file("proxy/reverse/http4s"))
  .settings(name := "reverse-proxy-http4s")
  .settings(crossDependencies(
    peknight.ext.http4s,
    peknight.ext.fs2,
    http4s.client,
    http4s.server,
  ))

lazy val socks = (project in file("socks"))
  .settings(name := "socks")
  .aggregate(
    socksCore.jvm,
    socksCore.js,
    socksCore.native,
    socks5,
  )

lazy val socksCore = (crossProject(JVMPlatform, JSPlatform, NativePlatform) in file("socks/core"))
  .settings(name := "socks-core")
  .settings(crossDependencies(peknight.error))

lazy val socks5 = (project in file("socks/socks5"))
  .settings(name := "socks5")
  .aggregate(
    socks5Core.jvm,
    socks5Core.js,
    socks5Api.jvm,
    socks5Api.js,
    socks5Server,
    socks5Client,
  )

lazy val socks5Core = (crossProject(JVMPlatform, JSPlatform) in file("socks/socks5/core"))
  .dependsOn(socksCore)
  .settings(name := "socks5-core")
  .settings(crossDependencies(
    fs2,
    comcast.ip4s
  ))

lazy val socks5Api = (crossProject(JVMPlatform, JSPlatform) in file("socks/socks5/api"))
  .dependsOn(socks5Core)
  .settings(name := "socks5-api")
  .settings(crossDependencies(fs2.io))

lazy val socks5Server = (project in file("socks/socks5/server"))
  .settings(name := "socks5-server")
  .aggregate(
    socks5ServerCore.jvm,
    socks5ServerCore.js,
  )

lazy val socks5ServerCore = (crossProject(JVMPlatform, JSPlatform) in file("socks/socks5/server/core"))
  .dependsOn(socks5Api)
  .settings(name := "socks5-server-core")
  .settings(crossDependencies(peknight.ext.cats))

lazy val socks5Client = (project in file("socks/socks5/client"))
  .settings(name := "socks5-client")
  .aggregate(
    socks5ClientCore.jvm,
    socks5ClientCore.js,
  )

lazy val socks5ClientCore = (crossProject(JVMPlatform, JSPlatform) in file("socks/socks5/client/core"))
  .dependsOn(socks5Api)
  .settings(name := "socks5-client-core")
