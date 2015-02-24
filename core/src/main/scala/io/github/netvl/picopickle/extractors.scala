package io.github.netvl.picopickle

import shapeless._
import shapeless.ops.function.FnToProduct

import scala.collection.generic.CanBuildFrom
import scala.language.higherKinds

trait ExtractorsComponent {
  this: BackendComponent =>

  object extractors {
    type Extractor[T] = PartialFunction[backend.BValue, T]

    val `null`: Extractor[Null] = {
      case backend.Get.Null(_) => null
    }

    val bool: Extractor[Boolean] = {
      case backend.Extract.Boolean(b) => b
    }

    val num: Extractor[Number] = {
      case backend.Extract.Number(n) => n
    }

    val str: Extractor[String] = {
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
          : Aux[(String, Extractor[A]) :: T, A :: MT] =
        new ObjectMapping[(String, Extractor[A]) :: T] {
          type Out = A :: MT

          override def isDefinedAt(bv: backend.BObject, s: (String, Extractor[A]) :: T): Boolean = s match {
            case (k, m) :: t => backend.getObjectKey(bv, k).exists(m.isDefinedAt) && tm.isDefinedAt(bv, t)
          }

          override def apply(bv: backend.BObject, s: (String, Extractor[A]) :: T): Option[A :: MT] = s match {
            case (k, m) :: t =>
              for {
                v <- backend.getObjectKey(bv, k)
                mv <- m.lift(v)
                mt <- tm(bv, t)
              } yield mv :: mt
          }
        }
    }

    def obj[S <: HList](extractors: S)(implicit m: ObjectMapping[S]): Extractor[m.Out] = {
      new PartialFunction[backend.BValue, m.Out] {
        override def isDefinedAt(bv: backend.BValue) = bv match {
          case backend.Get.Object(o) => m.isDefinedAt(o, extractors)
          case _ => false
        } 

        override def apply(bv: backend.BValue) = bv match {
          case backend.Get.Object(o) => m(o, extractors).get
        }
      }
    }

    object arr {
      class LikeBuilder[C[_]] {
        def of[T](m: Extractor[T])(implicit cbf: CanBuildFrom[C[T], T, C[T]]): Extractor[C[T]] =
          new PartialFunction[backend.BValue, C[T]] {
            override def isDefinedAt(bv: backend.BValue) = bv match {
              case backend.Extract.Array(arr) => arr.forall(m.isDefinedAt)
              case _ => false
            }

            override def apply(bv: backend.BValue) = bv match {
              case backend.Extract.Array(arr) => arr.map(m)(collection.breakOut)
            }
          }
      }

      def like[C[_]] = new LikeBuilder[C]
    }

    implicit class ExtractorAndThenUnpacked[L <: HList](m: Extractor[L]) {
      def andThenUnpacked[F, U](f: F)(implicit tp: FnToProduct.Aux[F, L => U]): Extractor[U] =
        m.andThen(tp.apply(f))
    }

    implicit class NumberExtractorExt(m: Extractor[Number]) {
      def byte: Extractor[Byte] = m.andThen(_.byteValue)
      def short: Extractor[Short] = m.andThen(_.shortValue)
      def int: Extractor[Int] = m.andThen(_.intValue)
      def long: Extractor[Long] = m.andThen(_.longValue)
      def float: Extractor[Float] = m.andThen(_.floatValue)
      def double: Extractor[Double] = m.andThen(_.doubleValue)
    }
  }
}
