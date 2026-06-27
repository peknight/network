import com.peknight.build.gav.*
import com.peknight.build.sbt.*

commonSettings

lazy val network = (project in file("."))
  .settings(name := "network")
  .aggregate(networkCore.projectRefs *)
  .aggregate(proxy)
  .aggregate(socks)

lazy val networkCore = (projectMatrix in file("network-core"))
  .settings(name := "network-core")
  .jvmPlatform(scalaVersions = Seq(scala.scala3.version))
  .jsPlatform(scalaVersions = Seq(scala.scala3.version))
  .nativePlatform(scalaVersions = Seq(scala.scala3.version))

lazy val proxy = (project in file("proxy"))
  .settings(name := "proxy")
  .aggregate(reverseProxy)

lazy val reverseProxy = (project in file("proxy/reverse"))
  .settings(name := "reverse-proxy")
  .aggregate(reverseProxyHttp4s.projectRefs *)

lazy val reverseProxyHttp4s = (projectMatrix in file("proxy/reverse/http4s"))
  .settings(name := "reverse-proxy-http4s")
  .settings(libraryDependencies ++= dependencies(
    peknight.http4s,
    peknight.fs2,
    http4s.client,
    http4s.server,
  ))
  .jvmPlatform(scalaVersions = Seq(scala.scala3.version))
  .jsPlatform(scalaVersions = Seq(scala.scala3.version))

lazy val socks = (project in file("socks"))
  .settings(name := "socks")
  .aggregate(socksCore.projectRefs *)
  .aggregate(socks5)

lazy val socksCore = (projectMatrix in file("socks/core"))
  .settings(name := "socks-core")
  .settings(libraryDependencies ++= dependencies(peknight.error))
  .jvmPlatform(scalaVersions = Seq(scala.scala3.version))
  .jsPlatform(scalaVersions = Seq(scala.scala3.version))
  .nativePlatform(scalaVersions = Seq(scala.scala3.version))

lazy val socks5 = (project in file("socks/socks5"))
  .settings(name := "socks5")
  .aggregate(socks5Core.projectRefs *)
  .aggregate(socks5Api.projectRefs *)
  .aggregate(socks5Server)
  .aggregate(socks5Client)

lazy val socks5Core = (projectMatrix in file("socks/socks5/core"))
  .dependsOn(socksCore)
  .settings(name := "socks5-core")
  .settings(libraryDependencies ++= dependencies(
    fs2,
    comcast.ip4s,
  ))
  .jvmPlatform(scalaVersions = Seq(scala.scala3.version))
  .jsPlatform(scalaVersions = Seq(scala.scala3.version))

lazy val socks5Api = (projectMatrix in file("socks/socks5/api"))
  .dependsOn(socks5Core)
  .settings(name := "socks5-api")
  .settings(libraryDependencies ++= dependencies(fs2.io))
  .jvmPlatform(scalaVersions = Seq(scala.scala3.version))
  .jsPlatform(scalaVersions = Seq(scala.scala3.version))

lazy val socks5Server = (project in file("socks/socks5/server"))
  .settings(name := "socks5-server")
  .aggregate(socks5ServerCore.projectRefs *)

lazy val socks5ServerCore = (projectMatrix in file("socks/socks5/server/core"))
  .dependsOn(socks5Api)
  .settings(name := "socks5-server-core")
  .settings(libraryDependencies ++= dependencies(peknight.cats))
  .jvmPlatform(scalaVersions = Seq(scala.scala3.version))
  .jsPlatform(scalaVersions = Seq(scala.scala3.version))

lazy val socks5Client = (project in file("socks/socks5/client"))
  .settings(name := "socks5-client")
  .aggregate(socks5ClientCore.projectRefs *)

lazy val socks5ClientCore = (projectMatrix in file("socks/socks5/client/core"))
  .dependsOn(socks5Api)
  .settings(name := "socks5-client-core")
  .jvmPlatform(scalaVersions = Seq(scala.scala3.version))
  .jsPlatform(scalaVersions = Seq(scala.scala3.version))
