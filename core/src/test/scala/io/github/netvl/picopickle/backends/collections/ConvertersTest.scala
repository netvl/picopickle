package io.github.netvl.picopickle.backends.collections

import org.scalatest.{FreeSpec, ShouldMatchers}

import shapeless._

import scala.collection.immutable.TreeSet

object ConvertersTest {
  object ComplexObjects {
    case class A(x: Int, y: String, z: B)
    case class B(a: Boolean, b: Double)
  }
}

class ConvertersTest extends FreeSpec with ShouldMatchers {
  import ConvertersTest._
  import CollectionsPickler._
  import converters._

  "Converters" - {
    "should convert to and from backend representation" - {
      "null" in {
        `null`.isDefinedAt(null: Any) shouldBe true
        (`null`.fromBackend(null: Any): Any) shouldEqual (null: Any)

        `null`.isDefinedAt("something": Any) shouldBe false

        `null`.toBackend(null) shouldEqual (null: Any)
      }

      "booleans" in {
        bool.isDefinedAt(true) shouldBe true
        bool.fromBackend(true) shouldBe true

        bool.isDefinedAt(false) shouldBe true
        bool.fromBackend(false) shouldBe false

        bool.isDefinedAt("something") shouldBe false

        bool.toBackend(true) shouldBe true
        bool.toBackend(false) shouldBe false
      }

      "numbers" in {
        num.isDefinedAt(1: Int) shouldBe true
        num.fromBackend(1) shouldEqual 1

        num.isDefinedAt(10.1: Double) shouldBe true
        num.fromBackend(10.1) shouldEqual 10.1

        num.isDefinedAt("something") shouldBe false

        num.toBackend(133: Long) shouldEqual 133L
        num.toBackend(42.2f) shouldEqual 42.2f
      }

      "strings" in {
        str.isDefinedAt("abcde") shouldBe true
        str.fromBackend("abcde") shouldEqual "abcde"

        str.isDefinedAt(12345) shouldBe false

        str.toBackend("hello") shouldEqual "hello"
      }

      "objects" in {
        val m = obj {
          ("a" -> num.int) ::
          ("b" -> str) ::
          HNil
        }

        val t1 = Map("a" -> 123, "b" -> "hello")
        val t2 = Map("a" -> false, "b" -> "hello")
        val t3 = Map("b" -> "hello")
        val t4 = Map("a" -> 342, "b" -> "goodbye", "c" -> false)

        m.isDefinedAt(t1) shouldBe true
        m.fromBackend(t1) shouldEqual (123 :: "hello" :: HNil)

        m.isDefinedAt(t2) shouldBe false
        m.isDefinedAt(t3) shouldBe false

        m.isDefinedAt(t4) shouldBe true
        m.fromBackend(t4) shouldEqual (342 :: "goodbye" :: HNil)

        m.toBackend(234 :: "blabla" :: HNil) shouldEqual Map("a" -> 234, "b" -> "blabla")
      }

      "complex classes" in {
        import ComplexObjects._

        val bc: Converter.Id[B] = unlift(B.unapply) >>> obj {
          "a" -> bool ::
          "b" -> num.double ::
          HNil
        } >>> B.apply _

        val ac: Converter.Id[A] = unlift(A.unapply) >>> obj {
          "x" -> num.int ::
          "y" -> str ::
          "z" -> bc ::
          HNil
        } >>> A.apply _

        val s = A(
          10,
          "hello",
          B(true, 42.4)
        )
        val t = Map(
          "x" -> 10,
          "y" -> "hello",
          "z" -> Map(
            "a" -> true,
            "b" -> 42.4
          )
        )

        ac.isDefinedAt(t) shouldBe true
        ac.fromBackend(t) shouldEqual s

        ac.toBackend(s) shouldEqual t
      }

      "homogeneous arrays" in {
        val cv = arr.as[Vector] of num.int
        val cs = arr.as[Set] of num.int

        val c1 = Vector(1, 2, 3)
        cv.isDefinedAt(c1) shouldBe true
        cv.fromBackend(c1) shouldEqual Vector(1, 2, 3)
        cv.toBackend(Vector(1, 2, 3)) shouldEqual Vector(1, 2, 3)
        cs.isDefinedAt(c1) shouldBe true
        cs.fromBackend(c1) shouldEqual Set(1, 2, 3)
        cs.toBackend(TreeSet(1, 2, 3)) shouldEqual Vector(1, 2, 3)

        val c2 = Vector("a", "e")
        cv.isDefinedAt(c2) shouldBe false
        cs.isDefinedAt(c2) shouldBe false
      }
    }
  }
}
