package io.github.netvl.picopickle.backends.jawn

import collection.immutable.ListMap

import org.scalatest.{ShouldMatchers, FreeSpec}

import io.github.netvl.picopickle.Fixtures._

class JsonPicklerTest extends FreeSpec with ShouldMatchers {
  import JsonPickler._

  def testRW[T: Reader: Writer](t: T, j: String): Unit = {
    val s = writeString(t)
    s shouldEqual j
    val u = readString[T](s)
    u shouldEqual t
  }

  "A json pickler" - {
    "should serialize and deserialize" - {
      "numbers to numbers" in {
        testRW(1: Byte, "1")
        testRW(2: Short, "2")
        testRW(3: Int, "3")
        testRW(4: Long, "4")
        testRW(5: Float, "5")
        testRW(6: Double, "6")
        testRW('a', "\"a\"")
      }

      "string to string" in {
        testRW("hello world", "\"hello world\"")
      }

      "null to null" in {
        testRW(null, "null")
      }

      "unit to an empty object" in {
        testRW((), "{}")
      }

      "boolean to boolean" in {
        testRW(true, "true")
        testRW(false, "false")
      }

      "collection to an array" in {
        testRW(Seq("a", "b"), """["a","b"]""")
      }

      "map to an array of arrays" in {
        testRW(ListMap(1 -> 2, 3 -> 4), """[[1,2],[3,4]]""")
      }

      "map with string keys to an object" in {
        testRW(Map("a" -> 1, "b" -> 2), """{"a":1,"b":2}""")
      }

      "case class to an object" in {
        import CaseClass._

        testRW(A(10, "hi"), """{"x":10,"y":"hi"}""")
      }

      "case object to an empty object" in {
        import CaseObject._

        testRW(A, "{}")
      }

      "sealed trait hierarchy to an object with a discriminator key" in {
        import SealedTrait._

        testRW[Root](
          A(12, "hello"),
          """{"$variant":"A","x":12,"y":"hello"}"""
        )
        testRW[Root](
          B(42L, Vector(1.0, 2.0, 3.0)),
          """{"$variant":"B","a":42,"b":[1,2,3]}"""
        )
        testRW[Root](C, """{"$variant":"C"}""")
      }

      "recursive types" in {
        import Recursives._

        testRW[Root](A, """{"$variant":"A"}""")
        testRW[Root](
          B(1, Some(B(2, Some(B(3, None))))),
          """{"$variant":"B","x":1,"b":{"x":2,"b":{"x":3}}}"""
        )
        testRW[Root](
          C(A),
          """{"$variant":"C","next":{"$variant":"A"}}"""
        )
      }

      "fields and classes renamed with annotations" in {
        import Renames._

        testRW[Root](A, """{"$variant":"0"}""")
        testRW[Root](
          B(12, "hello"),
          """{"$variant":"B","x":12,"zzz":"hello"}"""
        )
      }
    }
  }
}
