import shapeless.Typeable

import sbt._

import scala.collection.convert.decorateAsScala._

import java.nio.file.Files

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import shapeless.syntax.typeable._
import ImplicitUtils._

sealed trait TestGenerator {
  def generate(config: Map[String, Any], input: Any): String
}
object TestGenerator {
  val Generators: Map[String, TestGenerator] = Map(
    "rw" -> RwTestGenerator
  )
  def forName(name: String): TestGenerator = Generators(name)
}

object RwTestGenerator extends TestGenerator {
  override def generate(config: Map[String, Any], input: Any) = ???
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

  def parseGlobal(root: Any): Map[String, Map[String, Any]] = root.ecast

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

  def loadDefinitions(projectDir: File): Vector[TestDefinition] = {
    val yaml = new Yaml(new SafeConstructor)
    (projectDir / "tests").get
      .map(_.toPath)
      .map(p => p.getFileName -> Files.readAllLines(p).asScala.mkString("\n"))
      .map { case (n, f) => n.toString.split("\\.")(0) -> YamlUtils.convertTree(yaml.load(f)) }
      .map { case (n, y) => parseDefinition(n, y) }
      .toVector
  }

  case class EvaluatedDefinition(projectName: String, fileName: String, packageName: String, body: String)

  def evaluateDefinition(definition: TestDefinition): Vector[EvaluatedDefinition] = {
    definition.variants.map { variant =>
      val projectName = variant.targetProject
      val fileName = definition.filePattern.interpolate(Map("name" -> variant.context("name")))
      val packageName = variant.context("package")
      val body = definition.template.interpolate(variant.context + ("cases" -> runGenerators(definition, variant)))
      EvaluatedDefinition(projectName, fileName, packageName, body)
    }
  }

  def runGenerators(definition: TestDefinition, variant: TestVariant): String = {
    val generatedTests = definition.cases.map(runGenerators(definition, variant, _))
    generatedTests.map(reindent(_, definition.indent)).mkString("\n\n")
  }

  def runGenerators(definition: TestDefinition, variant: TestVariant, testCase: TestCase): String = {
    testCase.items.toVector.map {
      case (k, v) =>
        val gen = TestGenerator.forName(k)
    }
    ???
  }

  def reindent(s: String, indent: Int): String = s  // TODO

  object Keys {
    def generatedFiles(root: File) = Def.task[Seq[File]] {
      Vector.empty
    }
  }
}

object Interpolation {
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
}

object YamlUtils {
  def convertTree(root: Any): Any = root match {
    case m: java.util.Map[Any, Any] => m.asScala.toMap.map {
      case (k, v) => convertTree(k) -> convertTree(v)
    }
    case s: java.util.Set[Any] => s.asScala.toSet.map(convertTree)
    case c: java.util.List[Any] => c.asScala.toVector.map(convertTree)
    case n: Int => n
    case n: Long => n
    case n: Double => n
  }
}

object ImplicitUtils {
  implicit class StringExt(val s: String) extends AnyVal {
    def interpolate(context: Map[String, Any]): String = Interpolation.interpolate(s, context)
  }

  implicit class AnyExt[T: Typeable: Manifest](val t: T) extends AnyVal {
    def ecast[U: Typeable: Manifest]: U = t.cast[U].getOrElse(
      throw new ClassCastException(s"Cannot cast ${manifest[T]} to ${manifest[U]}")
    )

    def mcast[A: Typeable: Manifest, B: Typeable: Manifest]: Map[A, B] = t match {
      case m: java.util.Map[_, _] => m.asScala.toMap.ecast[Map[A, B]]
    }

    def vcast[U: Typeable: Manifest]: Vector[U] = t match {
      case c: java.util.Collection[_] => c.asScala.toVector.ecast[Vector[U]]
    }

    def |>[U](f: T => U): U = f(t)
  }
}
