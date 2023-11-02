import sbt._
import play.sbt.PlaySettings
import scoverage.ScoverageSbtPlugin._

name := "knowlg-search"
ThisBuild / organization := "org.sunbird"
ThisBuild / scalaVersion := "2.12.8"

lazy val scalaMajorVersion = "2.12"

lazy val root = (project in file("."))
  .aggregate(searchService)
  .dependsOn(searchService)
  .settings(
    commonSettings
  )

lazy val searchService = (project in file("search-service"))
  .enablePlugins(PlayScala, PlayNettyServer, JacocoPlugin)
  .disablePlugins(PlayAkkaHttpServer)
  .settings(
    name := "search-service",
    version := "1.0-SNAPSHOT",
    commonSettings,
    libraryDependencies ++= Seq(
      guice,
      "javax.inject" % "javax.inject" % "1",
      "org.sunbird" % "actor-core" % "1.0-SNAPSHOT",
      "org.sunbird" % "search-core" % "1.0-SNAPSHOT",
      "org.joda" % "joda-convert" % "2.1.2",
      "com.github.danielwegener" % "logback-kafka-appender" % "0.2.0-RC2",
      "net.logstash.logback" % "logstash-logback-encoder" % "5.2",
      "io.lemonlabs" %% "scala-uri" % "1.4.10",
      "net.codingwell" %% "scala-guice" % "4.2.5",
      "com.typesafe.play" %% "play-specs2" % "2.7.9",
      "io.netty" % "netty-transport-native-epoll" % "4.1.60.Final",
      "com.typesafe.akka" %% "akka-testkit" % "2.5.22" % "test",
      "org.javassist" % "javassist" % "3.24.0-GA" % "test",
      "org.powermock" % "powermock-api-mockito2" % "2.0.9" % "test",
      "org.powermock" % "powermock-module-junit4" % "2.0.9" % "test",
      "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test,
      "org.scalatest" % ("scalatest_" + scalaMajorVersion) % "3.1.2" % Test
    )
  )




lazy val commonSettings = Seq(
  javacOptions ++= Seq("-source", "11", "-target", "11"),
  crossPaths := false,
  Test / compile / coverageEnabled := true,
  Compile / compile / coverageEnabled := false,
  resolvers ++= Seq("Local Maven Repository" at "file:///"+Path.userHome+"/.m2/repository")
)