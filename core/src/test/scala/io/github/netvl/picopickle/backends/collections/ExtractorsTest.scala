package io.github.netvl.picopickle.backends.collections

import org.scalatest.{FreeSpec, ShouldMatchers}
import shapeless._

import scala.collection.immutable.TreeMap

object ExtractorsTest {
  object ComplexObjects {
    case class A(x: Int, y: String, z: B)
    case class B(a: Boolean, b: Double)
  }
}

class ExtractorsTest extends FreeSpec with ShouldMatchers {
  import CollectionsPickler._
  import ExtractorsTest._
  import extractors._

  "Extractors" - {
    "should extract from backend representation" - {
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

      "object as map" in {
        val mm = obj.as[Map] to num.double
        val mt = obj.as[TreeMap] to num.double

        val t1 = Map.empty[String, Any]
        mm.isDefinedAt(t1) shouldBe true
        mm(t1) shouldBe 'empty
        mt.isDefinedAt(t1) shouldBe true
        mt(t1) shouldBe 'empty

        val t2 = Map[String, Any]("a" -> 12.3, "b" -> 13.4)
        mm.isDefinedAt(t2) shouldBe true
        mm(t2) shouldEqual Map("a" -> 12.3, "b" -> 13.4)
        mt.isDefinedAt(t2) shouldBe true
        mt(t2) shouldEqual TreeMap("a" -> 12.3, "b" -> 13.4)

        val t3 = Map[String, Any]("a" -> true, "b" -> Vector(1))
        mm.isDefinedAt(t3) shouldBe false
        mt.isDefinedAt(t3) shouldBe false
      }

      "homogenous arrays" in {
        val mv = arr.as[Vector] of num.int
        val ms = arr.as[Set] of num.int

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

      "heterogenous arrays" in {
        val ma = arr(str :: num :: arr.as[Set].of(bool) :: HNil)
        val me = arr(HNil: HNil)

        val c1 = Vector("a", 1, Vector(true, false, true))
        ma.isDefinedAt(c1) shouldBe true
        ma(c1) shouldEqual ("a" :: 1 :: Set(true, false) :: HNil)

        val c2 = Vector("too small")
        ma.isDefinedAt(c2) shouldBe false

        val c3 = Vector("too large", 1, Vector(true), "a", 34, 22.9, "zzz")
        ma.isDefinedAt(c3) shouldBe true
        ma(c3) shouldEqual ("too large" :: 1 :: Set(true) :: HNil)

        val c4 = Vector("incorrect types", true, Vector(false))
        ma.isDefinedAt(c4) shouldBe false

        val c5 = Vector()  // empty
        me.isDefinedAt(c1) shouldBe true
        me(c1) shouldEqual HNil
        me.isDefinedAt(c5) shouldBe true
        me(c5) shouldEqual HNil
      }

      "classes" in {
        import ComplexObjects._

        val m = obj(
          ("k" -> str) ::
          ("vs" -> arr.as[Set].of(extractors.value[A])) ::
          HNil
        ).andThen {
          case k :: vs :: HNil => (k, vs)
        }

        val t1 = Map(
          "k" -> "hello",
          "vs" -> Vector(
            Map(
              "x" -> 10,
              "y" -> "hello",
              "z" -> Map(
                "a" -> true,
                "b" -> 42.4
              )
            ),
            Map(
              "x" -> 10,
              "y" -> "hello",
              "z" -> Map(
                "a" -> true,
                "b" -> 42.4
              )
            ),
            Map(
              "x" -> 11,
              "y" -> "bye",
              "z" -> Map(
                "a" -> false,
                "b" -> -42.4
              )
            )
          )
        )
        m.isDefinedAt(t1) shouldBe true
        m(t1) shouldEqual ("hello", Set(A(10, "hello", B(true, 42.4)), A(11, "bye", B(false, -42.4))))
      }
    }
  }
}
