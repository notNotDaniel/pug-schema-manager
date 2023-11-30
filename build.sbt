ThisBuild / version := "1.1.0-SNAPSHOT"
ThisBuild / organization := "works.pugcode"
ThisBuild / scalaVersion := "2.13.6"

lazy val catsEffectVersion = "3.4.10"
lazy val doobieVersion = "1.0.0-RC2"

lazy val root = (project in file(".")).settings(
  name := "pug-schema-manager",
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-collections-core" % "0.9.6",

    // "core" module - IO, IOApp, schedulers
    "org.typelevel" %% "cats-effect" % catsEffectVersion,
    "org.typelevel" %% "cats-effect-kernel" % catsEffectVersion,
    "org.typelevel" %% "cats-effect-std" % catsEffectVersion,
    "org.typelevel" %% "cats-effect-testkit" % catsEffectVersion % Test,

    // better-monadic-for compiler plugin as suggested by cats-effect documentation
    compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test,

    // Doobie
    "org.tpolecat" %% "doobie-core" % doobieVersion,
    "org.tpolecat" %% "doobie-postgres" % doobieVersion,
    "org.tpolecat" %% "doobie-hikari" % doobieVersion,
    "org.tpolecat" %% "doobie-h2" % doobieVersion % Test,
    "org.tpolecat" %% "doobie-munit" % doobieVersion % Test,

    // Logging
    "ch.qos.logback" % "logback-classic" % "1.4.5",
    "org.typelevel" %% "log4cats-slf4j"   % "2.5.0",
  )
)

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-language:postfixOps",
  "-language:implicitConversions",
)
