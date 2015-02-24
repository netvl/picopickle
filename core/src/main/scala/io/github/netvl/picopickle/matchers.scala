package io.github.netvl.picopickle

import shapeless._
import shapeless.ops.function.FnToProduct

trait MatchersComponent {
  this: BackendComponent =>

  object matchers {
    type Matcher[T] = PartialFunction[backend.BValue, T]

    def `null`: Matcher[Null] = {
      case backend.Get.Null(_) => null
    }

    def bool: Matcher[Boolean] = {
      case backend.Extract.Boolean(b) => b
    }

    def num: Matcher[Number] = {
      case backend.Extract.Number(n) => n
    }

    def str: Matcher[String] = {
      case backend.Extract.String(s) => s
    }

    trait ObjectMapping[S <: HList] {
      type Out
      def isDefinedAt(bv: backend.BObject, s: S): Boolean
      def apply(bv: backend.BObject, s: S): Option[Out]
    }
    object ObjectMapping {
      type Aux[S <: HList, T <: HList] = ObjectMapping[S] { type Out = T }

      implicit val hnilObjectMapping: Aux[HNil, HNil] = new ObjectMapping[HNil] {
        type Out = HNil
        override def isDefinedAt(bv: backend.BObject, s: HNil) = true
        override def apply(bv: backend.BObject, t: HNil) = Some(HNil)
      }

      implicit def hconsObjectMapping[A, T <: HList, MT <: HList](implicit tm: ObjectMapping.Aux[T, MT])
          : Aux[(String, Matcher[A]) :: T, A :: MT] =
        new ObjectMapping[(String, Matcher[A]) :: T] {
          type Out = A :: MT

          override def isDefinedAt(bv: backend.BObject, s: (String, Matcher[A]) :: T): Boolean = s match {
            case (k, m) :: t => backend.getObjectKey(bv, k).exists(m.isDefinedAt) && tm.isDefinedAt(bv, t)
          }

          override def apply(bv: backend.BObject, s: (String, Matcher[A]) :: T): Option[A :: MT] = s match {
            case (k, m) :: t =>
              for {
                v <- backend.getObjectKey(bv, k)
                mv <- m.lift(v)
                mt <- tm(bv, t)
              } yield mv :: mt
          }
        }
    }

    def obj[S <: HList](matchers: S)(implicit m: ObjectMapping[S]): Matcher[m.Out] = {
      new PartialFunction[backend.BValue, m.Out] {
        override def isDefinedAt(bv: backend.BValue) = bv match {
          case backend.Get.Object(o) => m.isDefinedAt(o, matchers)
          case _ => false
        } 

        override def apply(bv: backend.BValue) = bv match {
          case backend.Get.Object(o) => m(o, matchers).get
        }
      }
    }

    implicit class MatcherAndThenUnpacked[L <: HList](m: Matcher[L]) {
      def andThenUnpacked[F, U](f: F)(implicit tp: FnToProduct.Aux[F, L => U]): Matcher[U] =
        m.andThen(tp.apply(f))
    }
  }
}
