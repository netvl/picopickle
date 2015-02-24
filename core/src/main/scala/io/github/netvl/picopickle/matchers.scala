package io.github.netvl.picopickle

import shapeless._
import shapeless.ops.hlist.Mapper

trait MatchersComponent {
  this: BackendComponent =>

  object matchers {
    type Matcher[T] = PartialFunction[backend.BValue, T]

    def num: Matcher[Number] = {
      case backend.Extract.Number(n) => n
    }

    def str: Matcher[String] = {
      case backend.Extract.String(s) => s
    }

    def obj[S <: HList](h: S)(implicit m: Mapper.Aux[_, S, ]): Matcher[T] = {
      // (String, Matcher[T1]) :: (String, Matcher[T2]) :: ... :: HNil =>
      // Option[T1] :: Option[T2] :: ... :: HNil
      def extractValuesFrom(x: backend.BObject) = new Poly1 {
        implicit def caseTuple[V] = at[(String, Matcher[V])] {
          case (k, m) => m(backend.getObjectKey(x, k).get)
        }
      }

      def checkDefinedIn(x: backend.BObject) = new Poly1 {
        implicit def `case`[V] = at[(String, Matcher[V])] {
          case (k, m) => backend.getObjectKey(x, k).exists(m.isDefinedAt)
        }
      }

      new PartialFunction[backend.BValue, T] {
        override def isDefinedAt(x: backend.BValue) = x match {
          case o: backend.BObject => h.foldMap(true)(checkDefinedIn(o))(_ && _)
          case _ => false
        }

        override def apply(x: backend.BValue) = x match {
          case o: backend.BObject => h.map(extractValuesFrom(o))
        }
      }
    }
  }
}
