name := "picopickle"

version := "0.1"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "com.chuusai" %% "shapeless" % "2.1.0" cross CrossVersion.full,
  compilerPlugin("org.scalamacros" %% "paradise" % "2.0.1" cross CrossVersion.full)
)