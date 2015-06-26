crossScalaVersions := Seq("2.10.5", "2.11.7")

val commonCommonSettings = Seq(
  organization := "io.github.netvl.picopickle",
  version := "0.1.4",
  scalaVersion := "2.11.7",

  autoAPIMappings := true
)

val commonSettings = commonCommonSettings ++ Seq(
  bintrayPackage := "picopickle",
  bintrayReleaseOnPublish in ThisBuild := false,

  licenses := Seq("MIT" -> url("https://raw.githubusercontent.com/netvl/picopickle/master/LICENSE")),
  homepage := Some(url("https://github.com/netvl/picopickle")),

  publishMavenStyle := true,

  pomExtra :=
    <developers>
      <developer>
        <name>Vladimir Matveev</name>
        <email>vladimir.matweev@gmail.com</email>
        <url>https://github.com/netvl</url>
      </developer>
    </developers>
    <scm>
      <connection>scm:git:https://github.com/netvl/picopickle</connection>
      <developerConnection>scm:git:git@github.com:netvl/picopickle.git</developerConnection>
      <url>https://github.com/netvl/picopickle</url>
    </scm>
)

def shapelessDependency(scalaVersion: String) = scalaVersion match {
  case v if v.startsWith("2.10") => Seq(
    "com.chuusai" %% "shapeless" % "2.2.3",
    compilerPlugin("org.scalamacros" %% "paradise" % "2.0.1" cross CrossVersion.full)
  )
  case _ => Seq("com.chuusai" %% "shapeless" % "2.2.3")
}

def commonDependencies(scalaVersion: String) =
  Seq("org.scalatest" %% "scalatest" % "2.2.5" % "test") ++
    shapelessDependency(scalaVersion)

lazy val core = project
  .settings(commonSettings: _*)
  .settings(
    name := "picopickle-core",

    libraryDependencies ++= commonDependencies(scalaVersion.value) ++ Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    ),

    sourceGenerators in Compile += task[Seq[File]] {
      val outFile = (sourceManaged in Compile).value / "io" / "github" / "netvl" / "picopickle" / "generated.scala"

      // TODO: this probably could be replaced with shapeless
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
           |  implicit def tuple${i}Reader[$types](implicit $readers): Reader[Tuple$i[$types]] =
           |    Reader {
           |      case backend.Extract.Array(Vector($vars)) =>
           |        Tuple$i($reads)
           |    }
         """.stripMargin

        val tupleWriter =
          s"""
           |  implicit def tuple${i}Writer[$types](implicit $writers): Writer[Tuple$i[$types]] =
           |    Writer {
           |      case Tuple$i($vars) => backend.makeArray(Vector($writes))
           |    }
           """.stripMargin

        (tupleReader, tupleWriter)
      }
      val (tupleReaders, tupleWriters) = tupleInstances.unzip

      val generatedSource =
        s"""
         |package io.github.netvl.picopickle
         |
         |trait TupleReaders {
         |  this: BackendComponent with TypesComponent =>
         |${tupleReaders.mkString("")}
         |}
         |
         |trait TupleWriters {
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
    },

    sourceGenerators in Test += TestGeneration.generatedFiles(sourceManaged in Test).taskValue
  )

lazy val jawn = project
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings: _*)
  .settings(
    name := "picopickle-backend-jawn",

    sourceGenerators in Test += TestGeneration.generatedFiles(sourceManaged in Test).taskValue,

    libraryDependencies ++= commonDependencies(scalaVersion.value) ++ Seq(
      "org.spire-math" %% "jawn-parser" % "0.8.0"
    )
  )

lazy val root = (project in file("."))
  .aggregate(core, jawn)
  .settings(commonCommonSettings: _*)
  .settings(unidocSettings: _*)
  .settings(site.settings ++ ghpages.settings: _*)
  .settings(
    name := "picopickle",

    site.addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), "latest/api"),
    git.remoteRepo := "git@github.com:netvl/picopickle.git",

    publish := {},
    publishLocal := {},
    packagedArtifacts := Map.empty
  )
