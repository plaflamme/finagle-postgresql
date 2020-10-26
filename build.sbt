import java.nio.charset.StandardCharsets

val finagleVersion = "20.4.0"
val specs2Version = "4.9.1"

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

lazy val finaglePostgresql = Project(id = "finagle-postgresql", base = file("finagle-postgresql"))
  .settings(
    name := "finagle-postgresql",
    base,
    sourceGenerators in Compile += genPgTypeTask,
    managedSourceDirectories in Compile += sourceManaged.value / "pg_type",
    libraryDependencies ++= Seq(
      "com.twitter" %% "finagle-netty4" % finagleVersion,
      "com.twitter" %% "util-stats" % finagleVersion,
      "org.specs2" %% "specs2-core" % specs2Version % "test,it",
      "org.specs2" %% "specs2-scalacheck" % specs2Version % "test,it",
      "org.specs2" %% "specs2-matcher-extra" % specs2Version % "test,it",
      "io.zonky.test" % "embedded-postgres" % "1.2.6" % IntegrationTest,
    ),
  )
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings),
    // This puts the `Test` classes on the IntegrationTest classpath.
    //   TODO: it's a bit verbose and opaque. Consider sharing source or some `test` subproject
    dependencyClasspath in IntegrationTest := (dependencyClasspath in IntegrationTest).value ++ (exportedProducts in Test).value,
    fork in IntegrationTest := true,
  )
