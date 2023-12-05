ThisBuild / tlBaseVersion := "0.1"
ThisBuild / organization := "works.pugcode"
ThisBuild / organizationName := "Pugilistic Codeworks"
ThisBuild / startYear := Some(2020)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers ++= List(
  // your GitHub handle and name
  tlGitHubDev("notNotDaniel", "Daniel Keller")
)
ThisBuild / tlSitePublishBranch := Some("main")

// TODO: scala 2.12 and scala3
val Scala213Version = "2.13.11"
ThisBuild / crossScalaVersions := Seq(Scala213Version)
ThisBuild / scalaVersion := Scala213Version

// Setup GitHub actions
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))

// Dependency versions
val catsEffectVersion = "3.4.10"
val doobieVersion = "1.0.0-RC2"

lazy val root = (project in file(".")).settings(
  name := "pug-schema-manager",
  description := "Functional schema management via Doobie",
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
    "org.typelevel" %% "log4cats-slf4j" % "2.5.0"
  )
)

lazy val docs = project
  .in(file("mdoc-project"))
  .dependsOn(root)
  .enablePlugins(TypelevelSitePlugin)

// Set compiler options
scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-language:postfixOps",
  "-language:implicitConversions"
)
