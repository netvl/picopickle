package io.github.netvl.picopickle.backends.collections

import org.scalatest.{FreeSpec, ShouldMatchers}

import shapeless._

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
    }
  }
}
