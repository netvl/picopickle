scalaVersion := "2.10.5"

libraryDependencies ++= Seq(
  "com.chuusai" %% "shapeless" % "2.2.3",
  "org.yaml" % "snakeyaml" % "1.15"
)

addCompilerPlugin("org.scalamacros" %% "paradise" % "2.0.1" cross CrossVersion.full)
