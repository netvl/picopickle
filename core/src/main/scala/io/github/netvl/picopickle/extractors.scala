package io.github.netvl.picopickle

import shapeless._
import shapeless.ops.function.FnToProduct

import scala.collection.generic.CanBuildFrom
import scala.language.higherKinds

trait ExtractorsComponent {
  this: BackendComponent with TypesComponent =>

  object extractors {
    type Extractor[+T] = PartialFunction[backend.BValue, T]

    def value[T](implicit rt: Reader[T]) = new PartialFunction[backend.BValue, T] {
      override def isDefinedAt(bv: backend.BValue): Boolean = rt.canRead(bv)
      override def apply(bv: backend.BValue): T = rt.read(bv)
    }

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
      def isDefinedAt(bo: backend.BObject, s: S): Boolean
      def apply(bo: backend.BObject, s: S): Option[Out]
    }
    object ObjectMapping {
      type Aux[S <: HList, T <: HList] = ObjectMapping[S] { type Out = T }

      implicit val hnilObjectMapping: Aux[HNil, HNil] = new ObjectMapping[HNil] {
        type Out = HNil
        override def isDefinedAt(bo: backend.BObject, s: HNil) = true
        override def apply(bo: backend.BObject, t: HNil) = Some(HNil)
      }

      implicit def hconsObjectMapping[A, T <: HList, MT <: HList](implicit tm: Aux[T, MT])
          : Aux[(String, Extractor[A]) :: T, A :: MT] =
        new ObjectMapping[(String, Extractor[A]) :: T] {
          type Out = A :: MT

          override def isDefinedAt(bo: backend.BObject, s: (String, Extractor[A]) :: T): Boolean = s match {
            case (k, m) :: t => backend.getObjectKey(bo, k).exists(m.isDefinedAt) && tm.isDefinedAt(bo, t)
          }

          override def apply(bo: backend.BObject, s: (String, Extractor[A]) :: T): Option[A :: MT] = s match {
            case (k, m) :: t =>
              for {
                v <- backend.getObjectKey(bo, k)
                mv <- m.lift(v)
                mt <- tm(bo, t)
              } yield mv :: mt
          }
        }
    }

    object obj {
      class AsBuilder[M[_, _]] {
        def to[V](m: Extractor[V])(implicit cbf: CanBuildFrom[M[String, V], (String, V), M[String, V]]): Extractor[M[String, V]] =
          new PartialFunction[backend.BValue, M[String, V]] {
            override def isDefinedAt(bv: backend.BValue): Boolean = bv match {
              case backend.Extract.Object(obj) => obj.values.forall(m.isDefinedAt)
              case _ => false
            }
            override def apply(bv: backend.BValue): M[String, V] = bv match {
              case backend.Extract.Object(obj) =>
                val b = cbf()
                b ++= obj.mapValues(m)
                b.result()
            }
          }
      }

      def as[M[_, _]] = new AsBuilder[M]

      def apply[S <: HList](extractors: S)(implicit m: ObjectMapping[S]): Extractor[m.Out] =
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
    
    trait ArrayMapping[S <: HList] {
      type Out
      def isDefinedAt(ba: backend.BArray, i: Int, s: S): Boolean
      def apply(ba: backend.BArray, i: Int, s: S): Option[Out]
    }
    object ArrayMapping {
      type Aux[S <: HList, T <: HList] = ArrayMapping[S] { type Out = T }

      implicit val hnilArrayMapping: Aux[HNil, HNil] = new ArrayMapping[HNil] {
        override type Out = HNil
        override def isDefinedAt(ba: backend.BArray, i: Int, s: HNil): Boolean = true
        override def apply(ba: backend.BArray, i: Int, s: HNil): Option[Out] = Some(HNil)
      }

      implicit def hconsArrayMapping[A, T <: HList, MT <: HList](implicit tm: Aux[T, MT])
          : Aux[Extractor[A] :: T, A :: MT] =
        new ArrayMapping[Extractor[A] :: T] {
          override type Out = A :: MT

          override def isDefinedAt(ba: backend.BArray, i: Int, s: Extractor[A] :: T): Boolean = s match {
            case m :: t if i < backend.getArrayLength(ba) =>
              m.isDefinedAt(backend.getArrayValueAt(ba, i)) && tm.isDefinedAt(ba, i+1, t)
            case _ => false
          }

          override def apply(ba: backend.BArray, i: Int, s: Extractor[A] :: T): Option[Out] = s match {
            case m :: t if i < backend.getArrayLength(ba) =>
              val bv = backend.getArrayValueAt(ba, i)
              for {
                mv <- m.lift(bv)
                mt <- tm(ba, i+1, t)
              } yield mv :: mt
            case _ => None
          }
        }
    }

    object arr {
      class AsBuilder[C[_]] {
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

      def as[C[_]] = new AsBuilder[C]
      
      def apply[S <: HList](extractors: S)(implicit m: ArrayMapping[S]): Extractor[m.Out] =
        new PartialFunction[backend.BValue, m.Out] {
          override def isDefinedAt(bv: backend.BValue): Boolean = bv match {
            case backend.Get.Array(ba) => m.isDefinedAt(ba, 0, extractors)
            case _ => false
          }

          override def apply(bv: backend.BValue): m.Out = bv match {
            case backend.Get.Array(ba) => m(ba, 0, extractors).get
          }
        }
    }

    implicit class NumberExtractorExt(m: Extractor[Number]) {
      def byte: Extractor[Byte] = m.andThen(_.byteValue)
      def short: Extractor[Short] = m.andThen(_.shortValue)
      def int: Extractor[Int] = m.andThen(_.intValue)
      def long: Extractor[Long] = m.andThen(_.longValue)
      def float: Extractor[Float] = m.andThen(_.floatValue)
      def double: Extractor[Double] = m.andThen(_.doubleValue)
    }

    implicit class ExtractorHListExt[L <: HList](m: Extractor[L]) {
      def >>>[F, U](f: F)(implicit tp: FnToProduct.Aux[F, L => U]): Extractor[U] =
        m.andThen(tp.apply(f))
    }

    implicit class ExtractorExt[T](m: Extractor[T]) {
      def ||(n: Extractor[T]): Extractor[T] = m orElse n

      def >>[U](f: T => U): Extractor[U] = m andThen f

      def ?? : Extractor[Option[T]] = {
        case bv => m.lift(bv)
      }

      def |+|[U](n: Extractor[U]): Extractor[Either[T, U]] = new Extractor[Either[T, U]] {
        override def isDefinedAt(bv: backend.BValue) = m.isDefinedAt(bv) || n.isDefinedAt(bv)
        override def apply(bv: backend.BValue) = bv match {
          case _ if m.isDefinedAt(bv) => Left(m(bv))
          case _ if n.isDefinedAt(bv) => Right(n(bv))
        }
      }
    }
  }
}
