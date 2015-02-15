package io.github.netvl.picopickle

import org.scalatest.{ShouldMatchers, FreeSpec}

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
}

class CollectionsPicklerTest extends FreeSpec with ShouldMatchers {
  import CollectionsPicklerTest._

  "A collections pickler" - {
    import CollectionsPickler._

    "should serialize" - {
      "numbers to numbers" in {
        write(1: Byte) shouldEqual (1: Byte)
        write(2: Short) shouldEqual (2: Short)
        write(3: Int) shouldEqual (3: Int)
        write(4: Long) shouldEqual (4: Long)
        write(5: Float) shouldEqual (5: Float)
        write(6: Double) shouldEqual (6: Double)
        write('a') shouldEqual 'a'
      }

      "string to string" in {
        write("hello world") shouldEqual "hello world"
      }

      "null to null" in {
        write(null) shouldBe (null: Any)
      }

      "unit to an empty map" in {
        write(()) shouldEqual Map()
      }

      "boolean to boolean" in {
        write(true) shouldEqual true
        write(false) shouldEqual false
      }

      "collection to a vector" in {
        write(Seq("a", "b")) shouldEqual Vector("a", "b")
      }

      "map to a vector of vectors" in {
        write(ListMap(1 -> 2, 3 -> 4)) shouldEqual Vector(Vector(1, 2), Vector(3, 4))
      }

      "map with string keys to an object" in {
        write(Map("a" -> 1, "b" -> 2)) shouldEqual Map("a" -> 1, "b" -> 2)
      }

      "case class to a map" in {
        import CaseClass._

        write(A(10, "hi")) shouldEqual Map(
          "x" -> 10,
          "y" -> "hi"
        )
      }

      "case object to an empty map" in {
        import CaseObject._

        write(A) shouldEqual Map()
      }

      "sealed trait hierarchy to a map with a discriminator" in {
        import SealedTrait._

        write[Root](A(12, "hello")) shouldEqual Map(
          "$variant" -> "A",
          "x" -> 12,
          "y" -> "hello"
        )
        write[Root](B(42L, Vector(1.0, 2.0, 3.0))) shouldEqual Map(
          "$variant" -> "B",
          "a" -> 42L,
          "b" -> Vector(1.0, 2.0, 3.0)
        )
        write[Root](C) shouldEqual Map("$variant" -> "C")
      }
    }
  }
}
