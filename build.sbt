crossScalaVersions := Seq("2.10.4", "2.11.5")

val commonSettings = Seq(
  organization := "io.github.netvl.picopickle",
  version := "0.1",
  scalaVersion := "2.10.4"
)

lazy val picopickle = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "picopickle",

    libraryDependencies ++= (scalaVersion.value match {
      case v if v.startsWith("2.11") => Seq(
        "com.chuusai" %% "shapeless" % "2.1.0"
      )
      case _ => Seq(
        "com.chuusai" %% "shapeless" % "2.1.0" cross CrossVersion.full,
        compilerPlugin("org.scalamacros" %% "paradise" % "2.0.1" cross CrossVersion.full)
      )
    }) ++ Seq(
      "org.scalatest" %% "scalatest" % "2.2.0" % "test"
    )

//    sourceGenerators in Compile += {
//      val outFile = (sourceManaged in Compile).value / "io" / "github" / "netvl" / "picopickle" / "generated.scala"
//
//      val tupleInstances = (1 to 22).map { i =>
//        def mkCommaSeparated(f: Int => String) = (1 to i).map(f).mkString(", ")
//        val types = mkCommaSeparated(j => s"T$j")
//        val readers = mkCommaSeparated(j => s"r$j: Reader[T$j]")
//
//        s"""
//           |    implicit def tuple${i}Reader[$types](implicit $readers): Reader[Tuple$i[$types]] =
//           |      Reader {
//           |
//           |      }
//         """.stripMargin
//      }
//    }
  )
