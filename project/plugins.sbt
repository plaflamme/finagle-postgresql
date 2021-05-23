addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.18")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.7.0")

libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"

// for StringOps
libraryDependencies += "com.twitter" %% "util-core" % "21.4.0"
