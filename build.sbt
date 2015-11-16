import sbt.Resolver

name := "joblist"
organization := "de.mpicbg.scicomp"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.7"

resolvers += Resolver.mavenLocal


//libraryDependencies += "com.github.pathikrit" %% "better-files" % "2.13.0"
//libraryDependencies += "com.github.pathikrit" %% "better-files" % "2.13.0-SNAPSHOT"

libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"

// needed for cli interface
libraryDependencies += "org.docopt" % "docopt" % "0.6.0-SNAPSHOT"

// common utilities
libraryDependencies += "de.mpicbg.scicomp" % "scalautils_2.11" % "0.1-SNAPSHOT"



initialCommands in(Test, console) := """ammonite.repl.Repl.run("")"""

