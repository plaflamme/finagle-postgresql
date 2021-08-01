import java.nio.charset.StandardCharsets
import sbt.{IntegrationTest => SbtIntegrationTest}

val finagleVersion = "21.6.0"
val specs2Version = "4.12.3"
val dockerItVersion = "0.10.0-beta9"

val scala212 = "2.12.12"
val scala213 = "2.13.3"
val scalaVersions = Seq(scala212, scala213)

val base = Seq(
  organization := "io.github.finagle",
  scalaVersion := scala212,
  crossScalaVersions := scalaVersions,
  publishArtifact in packageDoc := false, // TODO
)

lazy val root = Project(
  id = "root",
  base = file("."),
).settings(
  base,
  crossScalaVersions := Nil,
  publish / skip := true
).aggregate(
  finaglePostgresql
)

val genPgTypeTask = Def.task {
  val source = BuiltinTypeGenerator.generate((sourceDirectory in Compile).value / "pg" / "pg_type.dat")
  val output = sourceManaged.value / "pg_type" / "PgType.scala"
  IO.write(output, source, StandardCharsets.UTF_8)
  output :: Nil
}

// https://www.scala-sbt.org/1.x/docs/Testing.html#Custom+test+configuration
// By making IntegrationTest extend Test, we make test classes visible to IntegrationTest.
lazy val IntegrationTest = SbtIntegrationTest.extend(Test)

lazy val finaglePostgresql = Project(id = "finagle-postgresql", base = file("finagle-postgresql"))
  .settings(
    name := "finagle-postgresql",
    base,
    sourceGenerators in Compile += genPgTypeTask,
    managedSourceDirectories in Compile += sourceManaged.value / "pg_type",
    libraryDependencies ++= Seq(
      "com.twitter" %% "finagle-netty4" % finagleVersion,
      "com.twitter" %% "util-stats" % finagleVersion,

      "org.specs2" %% "specs2-core" % specs2Version % Test,
      "org.specs2" %% "specs2-scalacheck" % specs2Version % Test,
      "org.specs2" %% "specs2-matcher-extra" % specs2Version % Test,
      "org.typelevel" %% "jawn-parser" % "1.2.0" % Test,
      "org.typelevel" %% "jawn-ast" % "1.2.0" % Test,

      "org.postgresql" % "postgresql" % "42.2.23" % IntegrationTest,
      "com.whisk" %% "docker-testkit-core-shaded" % dockerItVersion % IntegrationTest,
      "ch.qos.logback" % "logback-classic" % "1.2.5" % IntegrationTest,
    ),
  )
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings),
    fork in IntegrationTest := true,
  )
