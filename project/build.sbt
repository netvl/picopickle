scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "com.chuusai" %% "shapeless" % "2.1.0" cross CrossVersion.full,
  "org.yaml" % "snakeyaml" % "1.15"
)
