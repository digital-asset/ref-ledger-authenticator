name := "authentication-service"

version := "0.1"

scalaVersion := "2.12.11"

val damlSDKVersion = "1.2.0"
val akkaVersion = "2.6.1"
val akkaHttpVersion = "10.1.11"

resolvers ++= Seq(
  Resolver.bintrayRepo("digitalassetsdk", "DigitalAssetSDK"),
  Resolver.mavenLocal
)

val checkerExclusionRule = ExclusionRule(organization = "org.checkerframework")

libraryDependencies ++= Seq(
  "com.daml" %% "bindings-akka" % damlSDKVersion excludeAll(checkerExclusionRule),
  "com.daml" %% "bindings-scala" % damlSDKVersion,
  "com.daml" %% "ledger-api-client" % damlSDKVersion,
  "com.daml" %% "ledger-api-domain" % damlSDKVersion,
  "com.daml" %% "ledger-api-scalapb" % damlSDKVersion,
  "com.daml" %% "rs-grpc-akka" % damlSDKVersion,
  "com.daml" % "rs-grpc-bridge" % damlSDKVersion,

  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,

  "com.pauldijou"     %% "jwt-spray-json" % "4.1.0",
  "com.auth0"         % "jwks-rsa" % "0.11.0",
  "org.bitbucket.b_c" % "jose4j" % "0.7.1",
  "com.google.guava"    % "guava" % "24.0-jre" excludeAll(checkerExclusionRule),
  "com.google.protobuf" % "protobuf-java" % "3.11.0",

  "com.pauldijou" %% "jwt-core" % "4.1.0",
  "com.thesamet.scalapb" %% "scalapb-runtime" % "0.9.0",
  "com.typesafe" % "config" % "1.4.0",
  "com.typesafe.akka" %% "akka-actor" % "2.6.1",
  "com.typesafe.akka" %% "akka-http-core" % "10.1.11",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "io.grpc" % "grpc-api" % "1.29.0",
  "io.netty" % "netty-handler" % "4.1.48.Final",
  "io.spray" %% "spray-json" % "1.3.5",
  "org.scalaz" %% "scalaz-core" % "7.2.24",
  "org.slf4j" % "slf4j-api" % "1.7.29",


  // Runtime Deps
  "ch.qos.logback" % "logback-classic" % "1.2.3" % Runtime,

  // Test Deps
  "org.scalatest" %% "scalatest" % "3.0.8" % Test,
  "org.scalamock" %% "scalamock" % "4.4.0" % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test
)

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "utf-8",
  "-unchecked",
  "-feature"
)

// NOTE: see Makefile for commands to generate this source
Compile / unmanagedSourceDirectories += baseDirectory.value / "scala-codegen" / "src" / "main" / "scala"

enablePlugins(PackPlugin)

packMain := Map("authentication-service" -> "com.projectdabl.authenticationservice.Main")

logLevel in assembly := Level.Debug

assemblyMergeStrategy in assembly := {
  case "banner.txt" | "logback.xml" =>
    MergeStrategy.first
  case "META-INF/io.netty.versions.properties" =>
    MergeStrategy.first
  case "module-info.class"  =>
    MergeStrategy.discard
  case PathList("google", "protobuf", xs @ _*) =>
    MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
