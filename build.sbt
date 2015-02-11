crossScalaVersions := Seq("2.10.4", "2.11.5")

val commonSettings = Seq(
  organization := "io.github.netvl.picopickle",
  version := "0.1",
  scalaVersion := "2.10.4"
)

val commonDependencies = Seq(
  "org.scalatest" %% "scalatest" % "2.2.0" % "test"
)

lazy val picopickle = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "picopickle",

    libraryDependencies ++= commonDependencies ++ (scalaVersion.value match {
      case v if v.startsWith("2.11") => Seq(
        "com.chuusai" %% "shapeless" % "2.1.0"
      )
      case _ => Seq(
        "com.chuusai" %% "shapeless" % "2.1.0" cross CrossVersion.full,
        compilerPlugin("org.scalamacros" %% "paradise" % "2.0.1" cross CrossVersion.full)
      )
    }),

    sourceGenerators in Compile += task[Seq[File]] {
      val outFile = (sourceManaged in Compile).value / "io" / "github" / "netvl" / "picopickle" / "generated.scala"

      val tupleInstances = (1 to 22).map { i =>
        def mkCommaSeparated(f: Int => String) = (1 to i).map(f).mkString(", ")
        val types = mkCommaSeparated(j => s"T$j")
        val readers = mkCommaSeparated(j => s"r$j: Reader[T$j]")
        val writers = mkCommaSeparated(j => s"w$j: Writer[T$j]")
        val vars = mkCommaSeparated(j => s"x$j")
        val reads = mkCommaSeparated(j => s"r$j.read(x$j)")
        val writes = mkCommaSeparated(j => s"w$j.write(x$j)")

        val tupleReader =
          s"""
           |    implicit def tuple${i}Reader[$types](implicit $readers): Reader[Tuple$i[$types]] =
           |      Reader {
           |        case backend.Extractors.Array(backend.From.Array(Vector($vars))) =>
           |          Tuple$i($reads)
           |      }
         """.stripMargin

        val tupleWriter =
          s"""
           |    implicit def tuple${i}Writer[$types](implicit $writers): Writer[Tuple$i[$types]] =
           |      Writer.fromPF {
           |        case (Tuple$i($vars), None) => backend.makeArray(Vector($writes))
           |      }
           """.stripMargin

        (tupleReader, tupleWriter)
      }
      val (tupleReaders, tupleWriters) = tupleInstances.unzip

      val generatedSource =
        s"""
         |package io.github.netvl.picopickle
         |
         |trait TupleReaders extends Tuple2Reader {
         |  this: BackendComponent with TypesComponent =>
         |${tupleReaders.mkString("")}
         |}
         |
         |trait TupleWriters extends Tuple2Writer {
         |  this: BackendComponent with TypesComponent =>
         |${tupleWriters.mkString("")}
         |}
         |
         |trait TupleReaderWritersComponent extends TupleReaders with TupleWriters {
         |  this: BackendComponent with TypesComponent =>
         |}
         """.stripMargin

      IO.write(outFile, generatedSource, IO.utf8)

      Seq(outFile)
    }
  )

lazy val jawn = project
  .settings(commonSettings: _*)
  .settings(
    name := "jawn-backend",

    libraryDependencies ++= commonDependencies ++ Seq(
      "org.spire-math" %% "jawn-parser" % "0.7.2"
    )
  )
