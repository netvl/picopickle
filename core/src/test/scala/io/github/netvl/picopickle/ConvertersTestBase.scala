package io.github.netvl.picopickle

import scala.collection.immutable.{TreeMap, TreeSet}
import scala.collection.mutable
import shapeless._

import org.scalatest.{FreeSpec, Matchers}

object ConvertersTestBase {
  object ComplexObjects {
    case class A(x: Int, y: String, z: B)
    case class B(a: Boolean, b: Double)
  }
}

trait ConvertersTestBase extends FreeSpec with Matchers with DefaultPickler {
  this: BackendComponent =>

  import backend._
  import conversionImplicits._
  import converters._
  import ConvertersTestBase.ComplexObjects._

  def backendName: String = "backend"

  "Converters" - {
    s"should convert to and from the $backendName representation" - {
      "null" in {
        `null`.isDefinedAt(makeNull) shouldBe true
        (`null`.fromBackend(makeNull): Any) shouldEqual (null: Any)

        `null`.isDefinedAt("something".toBackend) shouldBe false

        `null`.toBackend(null) shouldEqual (makeNull: Any)
      }

      "booleans" in {
        bool.isDefinedAt(true.toBackend) shouldBe true
        bool.fromBackend(true.toBackend) shouldBe true

        bool.isDefinedAt(false.toBackend) shouldBe true
        bool.fromBackend(false.toBackend) shouldBe false

        bool.isDefinedAt("something".toBackend) shouldBe false

        bool.toBackend(true) shouldBe true.toBackend
        bool.toBackend(false) shouldBe false.toBackend
      }

      "numbers" in {
        num.isDefinedAt((1: Int).toBackend) shouldBe true
        num.fromBackend((1: Int).toBackend) shouldEqual 1

        num.isDefinedAt((10.1: Double).toBackend) shouldBe true
        num.fromBackend((10.1: Double).toBackend) shouldEqual 10.1

        num.isDefinedAt("something".toBackend) shouldBe false

        num.toBackend(133: Long) shouldEqual 133L.toBackend
        num.toBackend(42.2f) shouldEqual 42.2f.toBackend
      }

      "strings" in {
        str.isDefinedAt("abcde".toBackend) shouldBe true
        str.fromBackend("abcde".toBackend) shouldEqual "abcde"

        str.isDefinedAt(12345.toBackend) shouldBe false

        str.toBackend("hello") shouldEqual "hello".toBackend
      }

      "objects" in {
        val m = obj {
          ("a" -> num.int) ::
          ("b" -> str) ::
          HNil
        }

        val t1 = Map("a" -> 123.toBackend, "b" -> "hello".toBackend).toBackend
        val t2 = Map("a" -> false.toBackend, "b" -> "hello".toBackend).toBackend
        val t3 = Map("b" -> "hello".toBackend).toBackend
        val t4 = Map("a" -> 342.toBackend, "b" -> "goodbye".toBackend, "c" -> false.toBackend).toBackend

        m.isDefinedAt(t1) shouldBe true
        m.fromBackend(t1) shouldEqual (123 :: "hello" :: HNil)

        m.isDefinedAt(t2) shouldBe false
        m.isDefinedAt(t3) shouldBe false

        m.isDefinedAt(t4) shouldBe true
        m.fromBackend(t4) shouldEqual (342 :: "goodbye" :: HNil)

        m.toBackend(234 :: "blabla" :: HNil) shouldEqual Map("a" -> 234.toBackend, "b" -> "blabla".toBackend).toBackend
      }

      "complex classes" in {

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
          "x" -> 10.toBackend,
          "y" -> "hello".toBackend,
          "z" -> Map(
            "a" -> true.toBackend,
            "b" -> 42.4.toBackend
          ).toBackend
        ).toBackend

        ac.isDefinedAt(t) shouldBe true
        ac.fromBackend(t) shouldEqual s

        ac.toBackend(s) shouldEqual t
      }

      "homogeneous arrays" in {
        val cv = arr.as[Vector] of num.int
        val cs = arr.as[Set] of num.int

        val c1 = Vector(1.toBackend, 2.toBackend, 3.toBackend).toBackend
        cv.isDefinedAt(c1) shouldBe true
        cv.fromBackend(c1) shouldEqual Vector(1, 2, 3)
        cv.toBackend(Vector(1, 2, 3)) shouldEqual c1
        cs.isDefinedAt(c1) shouldBe true
        cs.fromBackend(c1) shouldEqual Set(1, 2, 3)
        cs.toBackend(TreeSet(1, 2, 3)) shouldEqual c1

        val c2 = Vector("a".toBackend, "e".toBackend).toBackend
        cv.isDefinedAt(c2) shouldBe false
        cs.isDefinedAt(c2) shouldBe false
      }

      "heterogenous arrays" in {
        val ma = arr(str :: num :: arr.as[Set].of(bool) :: HNil)
        val me = arr(HNil: HNil)

        val c1 = Vector("a".toBackend, 1.toBackend, Vector(false.toBackend, true.toBackend).toBackend).toBackend
        val r1 = "a" :: (1: Number) :: Set(true, false) :: HNil
        ma.isDefinedAt(c1) shouldBe true
        ma.fromBackend(c1) shouldEqual r1
        ma.toBackend("a" :: (1: Number) :: TreeSet(false, true) :: HNil) shouldEqual c1

        val c2 = Vector("too small".toBackend).toBackend
        ma.isDefinedAt(c2) shouldBe false

        val c3 = Vector(
          "too large".toBackend, 1.toBackend, Vector(true.toBackend).toBackend, "a".toBackend,
          34.toBackend, 22.9.toBackend, "zzz".toBackend
        ).toBackend
        val r3 = "too large" :: (1: Number) :: Set(true) :: HNil
        ma.isDefinedAt(c3) shouldBe true
        ma.fromBackend(c3) shouldEqual r3
        ma.toBackend(r3) shouldEqual Vector("too large".toBackend, 1.toBackend, Vector(true.toBackend).toBackend).toBackend

        val c4 = Vector("incorrect types".toBackend, true.toBackend, Vector(false.toBackend).toBackend).toBackend
        ma.isDefinedAt(c4) shouldBe false

        val c5 = Vector().toBackend  // empty
        me.isDefinedAt(c1) shouldBe true
        me.fromBackend(c1) shouldEqual HNil
        me.isDefinedAt(c5) shouldBe true
        me.fromBackend(c5) shouldEqual HNil
        me.toBackend(HNil) shouldEqual c5
      }

      "object as map" in {
        val mm = obj.as[Map] to num.double
        val mt = obj.as[TreeMap] to num.double

        val t1 = Map.empty[String, BValue].toBackend
        mm.isDefinedAt(t1) shouldBe true
        mm.fromBackend(t1) shouldBe 'empty
        mm.toBackend(Map.empty) shouldEqual t1
        mt.isDefinedAt(t1) shouldBe true
        mt.fromBackend(t1) shouldBe 'empty
        mt.toBackend(TreeMap.empty) shouldEqual t1

        val t2 = Map[String, BValue]("a" -> 12.3.toBackend, "b" -> 13.4.toBackend).toBackend
        val s2m = Map("a" -> 12.3, "b" -> 13.4)
        val s2t = TreeMap("a" -> 12.3, "b" -> 13.4)
        mm.isDefinedAt(t2) shouldBe true
        mm.fromBackend(t2) shouldEqual s2m
        mm.toBackend(s2m) shouldEqual t2
        mt.isDefinedAt(t2) shouldBe true
        mt.fromBackend(t2) shouldEqual s2t
        mt.toBackend(s2t) shouldEqual t2

        val t3 = Map[String, BValue]("a" -> true.toBackend, "b" -> Vector(1.toBackend).toBackend).toBackend
        mm.isDefinedAt(t3) shouldBe false
        mt.isDefinedAt(t3) shouldBe false
      }

      "autoconverted classes" in {

        val m =
          {
            (k: String, vs: mutable.LinkedHashSet[A]) => k :: vs :: HNil
          }.tupled >> obj(
            "k" -> str ::
            "vs" -> arr.as[mutable.LinkedHashSet].of(converters.value[A]) ::
            HNil
          ) >> {
            case k :: vs :: HNil => (k, vs)
          }

        val t1 = Map(
          "k" -> "hello".toBackend,
          "vs" -> Vector(
            Map(
              "x" -> 10.toBackend,
              "y" -> "hello".toBackend,
              "z" -> Map(
                "a" -> true.toBackend,
                "b" -> 42.4.toBackend
              ).toBackend
            ).toBackend,
            Map(
              "x" -> 11.toBackend,
              "y" -> "bye".toBackend,
              "z" -> Map(
                "a" -> false.toBackend,
                "b" -> (-42.4).toBackend
              ).toBackend
            ).toBackend
          ).toBackend
        ).toBackend
        val r1 = ("hello", mutable.LinkedHashSet(A(10, "hello", B(true, 42.4)), A(11, "bye", B(false, -42.4))))
        m.isDefinedAt(t1) shouldBe true
        m.fromBackend(t1) shouldEqual r1
        m.toBackend(r1) shouldEqual t1
      }
    }
  }
}
