import sbtrelease.ReleasePlugin.autoImport.ReleaseKeys._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease._

val finagleVersion = "19.9.0"

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

lazy val finaglePostgresql = Project(
  id = "finagle-postgresql",
  base = file("finagle-postgresql"),
).settings(
  hopperBase,
  name := "finagle-postgresql",
  organization := "com.hopper",
  libraryDependencies ++= Seq(
    "com.twitter" %% "finagle-netty4" % finagleVersion,
    "com.twitter" %% "util-stats" % finagleVersion,
    "io.zonky.test" % "embedded-postgres" % "1.2.6" % "test",
  )
)
