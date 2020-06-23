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

libraryDependencies ++= Seq(
  "com.daml" %% "bindings-akka" % damlSDKVersion,
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

// There are name clashes between files in some dependencies. The assmebly
// plugin will by default error out if two files (from different dependencies)
// with the same name have different content, which is probably better than the
// default JVM behaviour of depending on classpath order to choose which one to
// load.
//
// See https://github.com/sbt/sbt-assembly#merge-strategy for more info.
assemblyMergeStrategy in assembly := {
 case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.discard
 case PathList("google", "protobuf", "field_mask.proto") => MergeStrategy.discard
 case PathList("module-info.class") => MergeStrategy.discard
 case PathList("org", "checkerframework", "checker", "nullness", "compatqual", "KeyForDecl.class") => MergeStrategy.first
 case PathList("org", "checkerframework", "checker", "nullness", "compatqual", "KeyForType.class") => MergeStrategy.first
 case PathList("org", "checkerframework", "checker", "nullness", "compatqual", "MonotonicNonNullDecl.class") => MergeStrategy.first
 case PathList("org", "checkerframework", "checker", "nullness", "compatqual", "NonNullDecl.class") => MergeStrategy.first
 case PathList("org", "checkerframework", "checker", "nullness", "compatqual", "PolyNullDecl.class") => MergeStrategy.first
 case PathList("org", "checkerframework", "checker", "nullness", "compatqual", "MonotonicNonNullType.class") => MergeStrategy.first
 case PathList("org", "checkerframework", "checker", "nullness", "compatqual", "NonNullType.class") => MergeStrategy.first
 case PathList("org", "checkerframework", "checker", "nullness", "compatqual", "NullableDecl.class") => MergeStrategy.first
 case PathList("org", "checkerframework", "checker", "nullness", "compatqual", "NullableType.class") => MergeStrategy.first
 case PathList("org", "checkerframework", "checker", "nullness", "compatqual", "PolyNullType.class") => MergeStrategy.first
 case PathList("org", "checkerframework", "checker", "nullness", "compatqual", "MonotonicNonNullType.class") => MergeStrategy.first
 case PathList("org", "checkerframework", "checker", "nullness", "compatqual", "NonNullType.class") => MergeStrategy.first
 case PathList("org", "checkerframework", "checker", "nullness", "compatqual", "NullableDecl.class") => MergeStrategy.first
 case PathList("org", "checkerframework", "checker", "nullness", "compatqual", "NullableType.class") => MergeStrategy.first
 case PathList("org", "checkerframework", "checker", "nullness", "compatqual", "PolyNullType.class") => MergeStrategy.first
 case x => (assemblyMergeStrategy in assembly).value(x)
}
