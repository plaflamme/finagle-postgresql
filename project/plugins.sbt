addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.20")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.8.2")

libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"

// for StringOps
libraryDependencies += "com.twitter" %% "util-core" % "21.5.0"
