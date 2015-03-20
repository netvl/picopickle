import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util

import ImplicitUtils._
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import sbt.Keys._
import sbt._
import shapeless.syntax.typeable._
import shapeless.{TypeCase, Typeable}

import scala.collection.convert.decorateAsScala._
import scala.language.higherKinds

sealed trait TestGenerator {
  def generate(config: Map[String, Any], variantName: String, input: Any): String
}
object TestGenerator {
  val Generators: Map[String, TestGenerator] = Map(
    "rw" -> RwTestGenerator
  )
  def forName(name: String): TestGenerator = Generators(name)
}

object RwTestGenerator extends TestGenerator {
  override def generate(config: Map[String, Any], variantName: String, input: Any): String = {
    require(variantName != "source", "variant name cannot be 'source'")

    val pattern = config("pattern").ecast[Vector[String]]
    val sourceIndex = pattern.indexOf("source")
    val variantIndex = pattern.indexOf(variantName)
    require(sourceIndex >= 0, s"source index is not set")
    require(variantIndex >= 0, s"unknown variant: $variantName")

    input.ecast[Vector[Any]].map { check =>
      val (kind, argType, sourceArg0, expectedArg0) = check match {
        case `Map[String, Any]`(m) =>
          val items = m("items").ecast[Vector[String]]
          (m.getOrElse("kind", "rw").ecast[String], m.get("type").ecast[Option[String]], items(sourceIndex), items(variantIndex))
        case `Vector[Any]`(c) =>
          ("rw", None, c(sourceIndex).ecast[String], c(variantIndex).ecast[String])
      }
      val (sourceArg, expectedArg) = (sourceArg0.trim, expectedArg0.trim)
      val finalArgType = argType.fold("")(t => s"[$t]")
      val invocation = kind match {
        case "rw" => s"testRW$finalArgType"
        case "r" => s"testR$finalArgType"
      }
      def tooLong(s: String) = s.contains("\n") || s.length > 45
      if (tooLong(sourceArg) || tooLong(expectedArg))
        s"""|$invocation(
            |${Strings.reindent(sourceArg, 2)},
            |${Strings.reindent(expectedArg, 2)}
            |)""".stripMargin
      else s"$invocation($sourceArg, $expectedArg)"
    }.mkString("\n")
  }
}

case class TestCase(name: String,
                    prepend: Option[String],
                    items: Map[String, Any])
case class TestVariant(name: String,
                       targetProject: String,
                       context: Map[String, String])
case class TestDefinition(name: String,
                          filePattern: String,
                          template: String,
                          indent: Int,
                          variants: Vector[TestVariant],
                          global: Map[String, Map[String, Any]],
                          cases: Vector[TestCase])

object TestGeneration {
  def parseCases(root: Any): Vector[TestCase] = {
    val v = root.ecast[Vector[Map[String, Any]]]
    v.map {
      case c =>
        TestCase(
          c("name").ecast[String],
          c.get("prepend").ecast[Option[String]],
          c - "name" - "prepend"
        )
    }
  }

  def parseVariants(root: Any): Vector[TestVariant] = {
    val m = root.ecast[Map[String, Map[String, String]]]
    m.map {
      case (k, v) => TestVariant(k, v("targetProject"), v)
    }.toVector
  }

  def parseGlobal(root: Any): Map[String, Map[String, Any]] = root.ecast[Map[String, Map[String, Any]]]

  def parseDefinition(name: String, root: Any): TestDefinition = {
    val m = root.ecast[Map[String, Any]]
    TestDefinition(
      name,
      m("file-pattern").ecast[String],
      m("template").ecast[String],
      m("indent").ecast[Integer],
      m("variants") |> parseVariants,
      m("global") |> parseGlobal,
      m("cases") |> parseCases
    )
  }

  def loadDefinitions(testsDir: File): Vector[TestDefinition] = {
    val yaml = new Yaml(new SafeConstructor)
    (testsDir ** "*.yml").get
      .map(_.toPath)
      .map(p => p.getFileName -> Files.readAllLines(p, StandardCharsets.UTF_8).asScala.mkString("\n"))
      .map { case (n, f) => n.toString.split("\\.")(0) -> YamlUtils.convertTree(yaml.load(f)) }
      .map { case (n, y) => parseDefinition(n, y) }
      .toVector
  }

  case class EvaluatedDefinition(projectName: String, fileName: String, packageName: String, body: String)

  def evaluateDefinitionIn(projectName: String, streams: TaskStreams, definition: TestDefinition): Option[EvaluatedDefinition] = {
    definition.variants.find(_.targetProject == projectName).map { variant =>
      val projectName = variant.targetProject
      val fileName = definition.filePattern.interpolate(Map("name" -> variant.context("name")))
      val packageName = variant.context("package")
      val body = definition.template.interpolate(variant.context + ("cases" -> runGenerators(definition, variant)))
      EvaluatedDefinition(projectName, fileName, packageName, body)
    } <| {
      case None =>
        streams.log.warn(s"No variant of test ${definition.name}.yml for project $projectName was found")
      case _ =>
    }
  }

  def runGenerators(definition: TestDefinition, variant: TestVariant): String = {
    val generatedTests = definition.cases.map(runGenerators(definition, variant, _))
    generatedTests.map(Strings.reindent(_, definition.indent)).mkString("\n\n")
  }

  def runGenerators(definition: TestDefinition, variant: TestVariant, testCase: TestCase): String = {
    val body = testCase.items.toVector.map {
      case (k, v) =>
        val gen = TestGenerator.forName(k)
        val (moreConfig, items) = v match {
          case `Map[String, Any]`(m) =>
            (m.getOrElse("config", Map.empty).ecast[Map[String, Any]], m("input").ecast[Vector[Any]])
          case `Vector[Any]`(c) =>
            (Map.empty[String, Any], c)
        }
        gen.generate(definition.global.getOrElse(k, Map.empty) ++ moreConfig, variant.name, items)
    }.mkString

    val finalBody = Strings.reindent(testCase.prepend.fold(body)(_ + '\n' + body), 2)
    s"""|"${testCase.name}" in {
        |$finalBody
        |}""".stripMargin
  }

  def generatedFiles(sourceRoot: SettingKey[File]) = Def.task[Seq[File]] {
    val projectId = thisProject.value.id
    val testDefinitions = loadDefinitions((baseDirectory in ThisBuild).value / "project" / "tests")
    testDefinitions.flatMap(evaluateDefinitionIn(projectId, streams.value, _)).map { definition =>
      val pkg = definition.packageName.replace('.', File.separatorChar)
      val targetFile = sourceRoot.value / pkg / definition.fileName
      IO.write(targetFile, definition.body, IO.utf8)
      targetFile
    }
  }
}

object Strings {
  def interpolate(s: String, context: Map[String, Any]): String = {
    val result = new StringBuilder
    var i = 0
    while (i < s.length) {
      val j = s.indexOf('$', i)
      if (j == -1) {
        result ++= s.substring(i)
        i = s.length
      } else if (j < s.length-1) {
        result ++= s.substring(i, j)
        if (s(j+1) == '$') {
          result += '$'
          i = j+2
        } else if (s(j+1) == '{') {
          val k = s.indexOf('}', j+2)
          if (k != -1) {
            val key = s.substring(j+2, k)
            result ++= context(key).toString
            i = k+1
          } else {
            result ++= "${"
            i = j+2
          }
        } else {
          result += '$'
          i = j+1
        }
      } else {
        result += '$'
        i = s.length
      }
    }
    result.toString()
  }

  def reindent(s: String, indent: Int): String = s.replaceAll("(?m)^", " " * indent)
}

object YamlUtils {
  def convertTree(root: Any): Any = root match {
    case m: util.Map[Any @unchecked, Any @unchecked] => m.asScala.toMap.map {
      case (k, v) => convertTree(k) -> convertTree(v)
    }
    case s: util.Set[Any @unchecked] => s.asScala.toSet.map(convertTree)
    case c: util.List[Any @unchecked] => c.asScala.toVector.map(convertTree)
    case n: Int => n
    case n: Long => n
    case n: Double => n
    case s: String => s
  }
}

object ImplicitUtils {
  val `Map[String, Any]` = TypeCase[Map[String, Any]]
  val `Vector[Any]` = TypeCase[Vector[Any]]

  implicit class StringExt(val s: String) extends AnyVal {
    def interpolate(context: Map[String, Any]): String = Strings.interpolate(s, context)
  }

  implicit class AnyExt[T: Manifest](val t: T) {
    def ecast[U: Typeable: Manifest]: U = t.cast[U].getOrElse(
      throw new ClassCastException(s"Cannot cast ${manifest[T]} to ${manifest[U]}")
    )

    def |>[U](f: T => U): U = f(t)

    def <|[U](f: T => U): T = { f(t); t }
  }

}
