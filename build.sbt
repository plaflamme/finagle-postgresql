import java.nio.charset.StandardCharsets
import sbt.{IntegrationTest => SbtIntegrationTest}

val finagleVersion = "20.4.0"
val specs2Version = "4.9.1"
// one of https://mvnrepository.com/artifact/io.zonky.test.postgres/embedded-postgres-binaries-bom
// though > 12.1.0 doesn't work on MacOS: https://github.com/zonkyio/embedded-postgres-binaries/issues/21
// We'll have to switch to docker at some point,
//   but we do a lot of setup from code which is unlikely to work with docker + CI.
val pgVersion = sys.props.getOrElse("POSTGRES_VERSION", "12.1.0")

val scala212 = "2.12.12"
val scala213 = "2.13.3"
val scalaVersions = Seq(scala212, scala213)

val base = Seq(
  organization := "com.hopper",
  scalaVersion := scala212,
  crossScalaVersions := Seq(scala212, scala213)
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
      "org.typelevel" %% "jawn-parser" % "1.0.0" % Test,
      "org.typelevel" %% "jawn-ast" % "1.0.0" % Test,
      "io.zonky.test" % "embedded-postgres" % "1.2.6" % IntegrationTest,
      "io.zonky.test.postgres" % "embedded-postgres-binaries-linux-amd64" % pgVersion % IntegrationTest,
      "io.zonky.test.postgres" % "embedded-postgres-binaries-darwin-amd64" % pgVersion % IntegrationTest,
    ),
  )
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings),
    fork in IntegrationTest := true,
  )
