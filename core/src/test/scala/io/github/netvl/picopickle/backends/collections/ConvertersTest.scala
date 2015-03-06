package io.github.netvl.picopickle.backends.collections

import org.scalatest.{FreeSpec, ShouldMatchers}

import shapeless._

import scala.collection.immutable.{TreeMap, TreeSet}

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

      "heterogenous arrays" in {
        val ma = arr(str :: num :: arr.as[Set].of(bool) :: HNil)
        val me = arr(HNil: HNil)

        val c1 = Vector("a", 1, Vector(true, false, true))
        val r1 = "a" :: (1: Number) :: Set(true, false) :: HNil
        ma.isDefinedAt(c1) shouldBe true
        ma.fromBackend(c1) shouldEqual r1
        ma.toBackend("a" :: (1: Number) :: TreeSet(false, true) :: HNil) shouldEqual Vector("a", 1, Vector(false, true))

        val c2 = Vector("too small")
        ma.isDefinedAt(c2) shouldBe false

        val c3 = Vector("too large", 1, Vector(true), "a", 34, 22.9, "zzz")
        val r3 = "too large" :: (1: Number) :: Set(true) :: HNil
        ma.isDefinedAt(c3) shouldBe true
        ma.fromBackend(c3) shouldEqual r3
        ma.toBackend(r3) shouldEqual Vector("too large", 1, Vector(true))

        val c4 = Vector("incorrect types", true, Vector(false))
        ma.isDefinedAt(c4) shouldBe false

        val c5 = Vector()  // empty
        me.isDefinedAt(c1) shouldBe true
        me.fromBackend(c1) shouldEqual HNil
        me.isDefinedAt(c5) shouldBe true
        me.fromBackend(c5) shouldEqual HNil
        me.toBackend(HNil) shouldEqual c5
      }

      "object as map" in {
        val mm = obj.as[Map] to num.double
        val mt = obj.as[TreeMap] to num.double

        val t1 = Map.empty[String, Any]
        mm.isDefinedAt(t1) shouldBe true
        mm.fromBackend(t1) shouldBe 'empty
        mm.toBackend(Map.empty) shouldEqual t1
        mt.isDefinedAt(t1) shouldBe true
        mt.fromBackend(t1) shouldBe 'empty
        mt.toBackend(TreeMap.empty) shouldEqual t1

        val t2 = Map[String, Any]("a" -> 12.3, "b" -> 13.4)
        mm.isDefinedAt(t2) shouldBe true
        mm.fromBackend(t2) shouldEqual Map("a" -> 12.3, "b" -> 13.4)
        mm.toBackend(Map("a" -> 12.3, "b" -> 13.4)) shouldEqual t2
        mt.isDefinedAt(t2) shouldBe true
        mt.fromBackend(t2) shouldEqual TreeMap("a" -> 12.3, "b" -> 13.4)
        mt.toBackend(TreeMap("a" -> 12.3, "b" -> 13.4)) shouldEqual t2

        val t3 = Map[String, Any]("a" -> true, "b" -> Vector(1))
        mm.isDefinedAt(t3) shouldBe false
        mt.isDefinedAt(t3) shouldBe false
      }
    }
  }
}
