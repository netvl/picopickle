package io.github.netvl.picopickle.backends.collections

import io.github.netvl.picopickle.key
import org.scalatest.{FreeSpec, ShouldMatchers}

import scala.collection.immutable.ListMap

object CollectionsPicklerTest {
  object CaseClass {
    case class A(x: Int, y: String)
  }

  object CaseObject {
    case object A
  }

  object SealedTrait {
    sealed trait Root
    case class A(x: Int, y: String) extends Root
    case class B(a: Long, b: Vector[Double]) extends Root
    case object C extends Root
  }

  object Recursives {
    sealed trait Root
    case object A extends Root
    case class B(x: Int, b: Option[B]) extends Root
    case class C(next: Root) extends Root
  }

  object Renames {
    sealed trait Root
    @key("0") case object A extends Root
    case class B(x: Int, @key("zzz") y: String) extends Root
  }
}

class CollectionsPicklerTest extends FreeSpec with ShouldMatchers {
  import CollectionsPickler._
  import CollectionsPicklerTest._

  def testRW[T: Reader: Writer](t: T, a: Any): Unit = {
    val s = write(t)
    s shouldEqual a
    val u = read[T](s)
    u shouldEqual t
  }

  "A collections pickler" - {
    "should serialize and deserializer" - {
      "numbers to numbers" in {
        testRW(1: Byte, 1: Byte)
        testRW(2: Short, 2: Short)
        testRW(3: Int, 3: Int)
        testRW(4: Long, 4: Long)
        testRW(5: Float, 5: Float)
        testRW(6: Double, 6: Double)
        testRW('a', 'a')
      }

      "string to string" in {
        testRW("hello world", "hello world")
      }

      "null to null" in {
        testRW(null, null)
      }

      "unit to an empty map" in {
        testRW((), Map())
      }

      "boolean to boolean" in {
        testRW(true, true)
        testRW(false, false)
      }

      "collection to a vector" in {
        testRW(Seq("a", "b"), Vector("a", "b"))
      }

      "map to a vector of vectors" in {
        testRW(ListMap(1 -> 2, 3 -> 4), Vector(Vector(1, 2), Vector(3, 4)))
      }

      "map with string keys to a map" in {
        testRW(Map("a" -> 1, "b" -> 2), Map("a" -> 1, "b" -> 2))
      }

      "case class to a map" in {
        import CaseClass._

        testRW(
          A(10, "hi"),
          Map(
            "x" -> 10,
            "y" -> "hi"
          )
        )
      }

      "case object to an empty map" in {
        import CaseObject._

        testRW(A, Map())
      }

      "sealed trait hierarchy to a map with a discriminator" in {
        import SealedTrait._

        testRW[Root](
          A(12, "hello"),
          Map(
            "$variant" -> "A",
            "x" -> 12,
            "y" -> "hello"
          )
        )
        testRW[Root](
          B(42L, Vector(1.0, 2.0, 3.0)),
          Map(
            "$variant" -> "B",
            "a" -> 42L,
            "b" -> Vector(1.0, 2.0, 3.0)
          )
        )
        testRW[Root](C, Map("$variant" -> "C"))
      }

      "recursive types" in {
        import Recursives._

        testRW[Root](A, Map("$variant" -> "A"))
        testRW[Root](
          B(1, Some(B(2, Some(B(3, None))))),
          Map(
            "$variant" -> "B",
            "x" -> 1,
            "b" -> Map(
              "x" -> 2,
              "b" -> Map(
                "x" -> 3
              )
            )
          )
        )
        testRW[Root](
          C(A),
          Map(
            "$variant" -> "C",
            "next" -> Map(
              "$variant" -> "A"
            )
          )
        )
      }

      "fields and classes renamed with annotations" in {
        import Renames._

        testRW[Root](
          A,
          Map("$variant" -> "0")
        )
        testRW[Root](
          B(12, "hello"),
          Map(
            "$variant" -> "B",
            "x" -> 12,
            "zzz" -> "hello"
          )
        )
      }
    }
  }
}
