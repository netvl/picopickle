package io.github.netvl.picopickle

object Fixtures {
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

  object Defaults {
    sealed trait Root
    case class A(x: Int, name: String = "me", enabled: Boolean = false) extends Root
  }

  object WithOverloadedApplyMethod {
    case class A(x: Int, y: String, z: Double = 12.3)

    object A {
      def apply(s: String): A = A(s.toInt, s, s.toDouble)
    }
  }

  object WithOverloadedConstructor {
    case class A(x: Int, y: String, z: Double = 12.3) {
      def this(s: String) = this(s.toInt, s, s.toDouble)
    }
  }
}
