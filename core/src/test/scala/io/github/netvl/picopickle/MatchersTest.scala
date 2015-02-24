package io.github.netvl.picopickle

import org.scalatest.{ShouldMatchers, FreeSpec}

object MatchersTest {
  object ComplexObjects {
    case class A(x: Int, y: String, z: B)
    case class B(a: Boolean, b: Double)
  }
}

class MatchersTest extends FreeSpec with ShouldMatchers {
  import MatchersTest._
  import CollectionsPickler._
  import matchers._

  "Matchers" - {
    "should extract" - {
      "null" in {
        `null`.isDefinedAt(null: Any) shouldBe true
        (`null`(null: Any): Any) shouldBe (null: Any)

        `null`.isDefinedAt("something") shouldBe false
      }

      "booleans" in {
        bool.isDefinedAt(true) shouldBe true
        bool(true) shouldBe true

        bool.isDefinedAt(false) shouldBe true
        bool(false) shouldBe false

        bool.isDefinedAt("something") shouldBe false
      }

      "numbers" in {
        num.isDefinedAt(1: Int) shouldBe true
        num(1) shouldEqual 1

        num.isDefinedAt(10.1: Double) shouldBe true
        num(10.1) shouldEqual 10.1

        num.isDefinedAt("something") shouldBe false
      }

      "strings" in {
        str.isDefinedAt("abcde") shouldBe true
        str("abcde") shouldEqual "abcde"

        str.isDefinedAt(12345) shouldBe false
      }

      "objects" in {
        import shapeless._

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
        m(t1) shouldEqual (123 :: "hello" :: HNil)

        m.isDefinedAt(t2) shouldBe false
        m.isDefinedAt(t3) shouldBe false

        m.isDefinedAt(t4) shouldBe true
        m(t4) shouldEqual (342 :: "goodbye" :: HNil)
      }

      "complex objects" in {
        import shapeless._
        import ComplexObjects._

        val bm = obj {
          ("a" -> bool) ::
          ("b" -> num.double) ::
          HNil
        }.andThenUnpacked(B.apply _)

        val am = obj {
          ("x" -> num.int) ::
          ("y" -> str) ::
          ("z" -> bm) ::
          HNil
        }.andThenUnpacked(A.apply _)

        val t = Map(
          "x" -> 10,
          "y" -> "hello",
          "z" -> Map(
            "a" -> true,
            "b" -> 42.4
          )
        )

        am.isDefinedAt(t) shouldBe true
        am(t) shouldEqual A(10, "hello", B(true, 42.4))
      }

      "arrays" in {
        val mv = arr.like[Vector] of num.int
        val ms = arr.like[Set] of num.int

        val c1 = Vector(1, 2, 3)
        mv.isDefinedAt(c1) shouldBe true
        mv(c1) shouldEqual Vector(1, 2, 3)

        val c2 = Vector(1, 2, 3)
        ms.isDefinedAt(c2) shouldBe true
        ms(c2) shouldEqual Set(1, 2, 3)

        val c3 = Vector("a", "e")
        mv.isDefinedAt(c3) shouldBe false
        ms.isDefinedAt(c3) shouldBe false
      }
    }
  }
}
