package io.github.netvl.picopickle

import shapeless._
import shapeless.ops.function.FnToProduct

import scala.language.implicitConversions

trait ConvertersComponent {
  this: BackendComponent with TypesComponent =>

  object converters {
    trait Converter[-T, +U] {
      self =>

      def toBackend(v: T): backend.BValue
      def isDefinedAt(bv: backend.BValue): Boolean
      def fromBackend(bv: backend.BValue): U

      final def >>[U1](f: U => U1): Converter[T, U1] = this andThen f
      final def andThen[U1](f: U => U1): Converter[T, U1] = new Converter[T, U1] {
        override def toBackend(v: T): backend.BValue = self.toBackend(v)
        override def isDefinedAt(bv: backend.BValue): Boolean = self.isDefinedAt(bv)
        override def fromBackend(bv: backend.BValue): U1 = f(self.fromBackend(bv))
      }

      final def ||[U1 >: U](c: Converter[Nothing, U1]): Converter[T, U1] = this orElse c
      final def orElse[U1 >: U](c: Converter[Nothing, U1]): Converter[T, U1] = new Converter[T, U1] {
        override def toBackend(v: T): backend.BValue = self.toBackend(v)
        override def isDefinedAt(bv: backend.BValue): Boolean = self.isDefinedAt(bv) || c.isDefinedAt(bv)
        override def fromBackend(bv: backend.BValue): U1 = bv match {
          case _ if self.isDefinedAt(bv) => self.fromBackend(bv)
          case _ if c.isDefinedAt(bv) => c.fromBackend(bv)
        }
      }

      final def ?? : Converter[T, Option[U]] = this.lift
      final def lift: Converter[T, Option[U]] = new Converter[T, Option[U]] {
        override def toBackend(v: T): backend.BValue = self.toBackend(v)
        override def isDefinedAt(bv: backend.BValue): Boolean = true
        override def fromBackend(bv: backend.BValue): Option[U] =
          if (self.isDefinedAt(bv)) Some(self.fromBackend(bv))
          else None
      }
    }
    trait LowerPriorityImplicits {
      implicit def converterAsWriter[T](c: Converter[T, Any]): Writer[T] =
        Writer {
          case v => c.toBackend(v)
        }

      implicit def converterAsReader[U](c: Converter[Nothing, U]): Reader[U] =
        Reader {
          case bv if c.isDefinedAt(bv) => c.fromBackend(bv)
        }
    }
    object Converter extends LowerPriorityImplicits {
      type Id[T] = Converter[T, T]

      def onlyWriter[T](to: T => backend.BValue): Converter[T, Any] =
        apply(to) { case _ => throw new IllegalArgumentException }

      def onlyReader[U](from: PF[backend.BValue, U]): Converter[Nothing, U] =
        apply((_: Any) => throw new IllegalArgumentException)(from)

      def apply[T, U](to: T => backend.BValue)(from: PF[backend.BValue, U]): Converter[T, U] =
        new Converter[T, U] {
          override def toBackend(v: T): backend.BValue = to(v)
          override def isDefinedAt(bv: backend.BValue): Boolean = from.isDefinedAt(bv)
          override def fromBackend(bv: backend.BValue): U = from(bv)
        }

      implicit def converterAsReadWriter[T](c: Converter[T, T]): ReadWriter[T] =
        ReadWriter[T](c, c)

      implicit class ConverterHListOps[L, T](c: Converter[T, L]) {
        def >>>[F, V](f: F)(implicit ev: L <:< HList, tp: FnToProduct.Aux[F, L => V]): Converter[T, V] =
          this andThenUnpacked f
        def andThenUnpacked[F, V](f: F)(implicit ev: L <:< HList, tp: FnToProduct.Aux[F, L => V]): Converter[T, V] =
          c >> tp(f)
      }
    }

    def value[T](implicit wt: Writer[T], rt: Reader[T]): Converter.Id[T] =
      new Converter[T, T] {
        override def toBackend(v: T): backend.BValue = wt.write(v)
        override def isDefinedAt(bv: backend.BValue) = rt.canRead(bv)
        override def fromBackend(bv: backend.BValue) = rt.read(bv)
      }

    val `null`: Converter.Id[Null] = Converter[Null, Null](_ => backend.makeNull) {
      case backend.Get.Null(_) => null
    }

    val bool: Converter.Id[Boolean] = Converter(backend.makeBoolean) {
      case backend.Extract.Boolean(b) => b
    }

    val num: Converter.Id[Number] = Converter(backend.makeNumber) {
      case backend.Extract.Number(n) => n
    }

    val str: Converter.Id[String] = Converter(backend.makeString) {
      case backend.Extract.String(s) => s
    }

    trait ObjectMapping[CC <: HList] {
      type In
      type Out

      def toBackend(in: In, cc: CC, bo: backend.BObject): backend.BObject
      def isDefinedAt(bo: backend.BObject, cc: CC): Boolean
      def fromBackend(bo: backend.BObject, cc: CC): Option[Out]
    }
    object ObjectMapping {
      type Aux[CC <: HList, In0 <: HList, Out0 <: HList] = ObjectMapping[CC] { type In = In0; type Out = Out0 }

      implicit val hlistObjectMapping: Aux[HNil, HNil, HNil] = new ObjectMapping[HNil] {
        override type In = HNil
        override type Out = HNil

        override def toBackend(in: HNil, cc: HNil, bo: backend.BObject): backend.BObject = bo
        override def isDefinedAt(bo: backend.BObject, cc: HNil): Boolean = true
        override def fromBackend(bo: backend.BObject, cc: HNil): Option[HNil] = Some(HNil)
      }

      implicit def hconsObjectMapping[T, U, CS <: HList, TS <: HList, US <: HList]
          (implicit tm: Aux[CS, TS, US]): Aux[(String, Converter[T, U]) :: CS, T :: TS, U :: US] =
        new ObjectMapping[(String, Converter[T, U]) :: CS] {
          override type In = T :: TS
          override type Out = U :: US

          override def toBackend(in: T :: TS, cc: (String, Converter[T, U]) :: CS, bo: backend.BObject): backend.BObject =
            (in, cc) match {
              case (t :: ts, (k, c) :: cs) =>
                val nbo = backend.setObjectKey(bo, k, c.toBackend(t))
                tm.toBackend(ts, cs, nbo)
            }
          override def isDefinedAt(bo: backend.BObject, cc: (String, Converter[T, U]) :: CS): Boolean = cc match {
            case (k, c) :: cs => backend.getObjectKey(bo, k).exists(c.isDefinedAt) && tm.isDefinedAt(bo, cs)
          }
          override def fromBackend(bo: backend.BObject, s: (String, Converter[T, U]) :: CS): Option[U :: US] = s match {
            case (k, c) :: cc =>
              for {
                v <- backend.getObjectKey(bo, k)
                mv <- c.lift.fromBackend(v)
                mt <- tm.fromBackend(bo, cc)
              } yield mv :: mt
          }
        }
    }
    
    object obj {
      def apply[S <: HList](converters: S)(implicit m: ObjectMapping[S]): Converter[m.In, m.Out] =
        new Converter[m.In, m.Out] {
          override def toBackend(v: m.In): backend.BValue =
            m.toBackend(v, converters, backend.makeEmptyObject)

          override def isDefinedAt(bv: backend.BValue): Boolean = bv match {
            case backend.Get.Object(bo) => m.isDefinedAt(bo, converters)
            case _ => false
          }

          override def fromBackend(bv: backend.BValue): m.Out = bv match {
            case backend.Get.Object(bo) => m.fromBackend(bo, converters).get
          }
        }
    }

    def unlift[T, U](f: T => Option[U]): T => U = t => f(t).get

    implicit class ConverterFunctionOps[V, T](f: V => T) {
      def >>[U](c: Converter[T, U]): Converter[V, U] =
        new Converter[V, U] {
          def isDefinedAt(bv: backend.BValue): Boolean = c.isDefinedAt(bv)
          def fromBackend(bv: backend.BValue): U = c.fromBackend(bv)
          def toBackend(v: V): backend.BValue = c.toBackend(f(v))
        }
    }

    implicit class ConverterProductFunctionOps[V, P <: Product](f: V => P) {
      def >>>[U, L <: HList](c: Converter[L, U])(implicit gen: Generic.Aux[P, L]): Converter[V, U] =
        new Converter[V, U] {
          def isDefinedAt(bv: backend.BValue): Boolean = c.isDefinedAt(bv)
          def fromBackend(bv: backend.BValue): U = c.fromBackend(bv)
          def toBackend(v: V): backend.BValue = c.toBackend(gen.to(f(v)))
        }
    }

    implicit class NumberConverterExt[U](m: Converter[Number, Number]) {
      private def conv[T](implicit f: T => Number): T => Number = f

      def byte: Converter.Id[Byte] = conv[Byte] >> m >> (_.byteValue)
      def short: Converter.Id[Short] = conv[Short] >> m >> (_.shortValue)
      def int: Converter.Id[Int] = conv[Int] >> m >> (_.intValue)
      def long: Converter.Id[Long] = conv[Long] >> m >> (_.longValue)
      def float: Converter.Id[Float] = conv[Float] >> m >> (_.floatValue)
      def double: Converter.Id[Double] = conv[Double] >> m >> (_.doubleValue)
    }
  }
}
