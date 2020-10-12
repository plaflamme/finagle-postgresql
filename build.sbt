import java.nio.charset.StandardCharsets

import com.hopper.sbt.HopperDeps
import sbtrelease.ReleasePlugin.autoImport.ReleaseKeys._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease._

val finagleVersion = "20.4.0"

lazy val inquireReleaseVersion: ReleaseStep = { st: State =>
  val extracted = Project.extract(st)

  val useDefs = st.get(useDefaults).getOrElse(false)
  val currentV = extracted.get(version)

  val releaseFunc = extracted.runTask(releaseVersion, st)._2
  val suggestedReleaseV = releaseFunc(currentV)

  //flatten the Option[Option[String]] as the get returns an Option, and the value inside is an Option
  val releaseV = readVersion(suggestedReleaseV, "Release version [%s] : ", useDefs, st.get(commandLineReleaseVersion).flatten)

  // The release steps are expecting a tuple but we don't use the next version so we just set it to a dummy value
  val nextV = ""
  st.put(versions, (releaseV, nextV))
}

lazy val root = Project(
  id = "root",
  base = file("."),
).settings(
  hopperBase,
  crossScalaVersions := Nil,
  organization := "com.hopper",
  releaseCrossBuild := false,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireReleaseVersion,
    runClean,
    releaseStepCommandAndRemaining("+test"),
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    pushChanges,
    releaseStepCommandAndRemaining("+publish"),
  )
).aggregate(
  finaglePostgresql
)

val genPgTypeTask = Def.task {
  val source = BuiltinTypeGenerator.generate((sourceDirectory in Compile).value / "pg" / "pg_type.dat")
  val output = sourceManaged.value / "pg_type" / "PgType.scala"
  IO.write(output, source, StandardCharsets.UTF_8)
  output :: Nil
}

lazy val finaglePostgresql = Project(
  id = "finagle-postgresql",
  base = file("finagle-postgresql"),
).settings(
  hopperBase,
  name := "finagle-postgresql",
  organization := "com.hopper",
  sourceGenerators in Compile += genPgTypeTask,
  managedSourceDirectories in Compile += sourceManaged.value / "pg_type",
  libraryDependencies ++= Seq(
    "com.twitter" %% "finagle-netty4" % finagleVersion,
    "com.twitter" %% "util-stats" % finagleVersion,

    // put specs on the IntegrationTest classpath
    HopperDeps.CompileDeps.specs2Core % "test,it",
    HopperDeps.CompileDeps.specs2Scalacheck % "test,it",
    HopperDeps.CompileDeps.specs2Mock % "test,it",
    HopperDeps.CompileDeps.specs2Junit % "test,it",
    HopperDeps.CompileDeps.specs2MatcherExtra % "test,it",
    "io.zonky.test" % "embedded-postgres" % "1.2.6" % IntegrationTest,
  ),
  Defaults.itSettings,
).configs(IntegrationTest)
  .settings(
    // This puts the `Test` classes on the IntegrationTest classpath.
    //   TODO: it's a bit verbose and opaque. Consider sharing source or some `test` subproject
    dependencyClasspath in IntegrationTest := (dependencyClasspath in IntegrationTest).value ++ (exportedProducts in Test).value,
    fork in IntegrationTest := true,
  )
