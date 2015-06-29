package io.github.netvl.picopickle

import shapeless._
import shapeless.ops.function.FnToProduct

import scala.collection.breakOut
import scala.collection.generic.CanBuildFrom
import scala.language.{higherKinds, implicitConversions}

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

      final def lift: Converter[T, Option[U]] = new Converter[T, Option[U]] {
        override def toBackend(v: T): backend.BValue = self.toBackend(v)
        override def isDefinedAt(bv: backend.BValue): Boolean = true
        override def fromBackend(bv: backend.BValue): Option[U] =
          if (self.isDefinedAt(bv)) Some(self.fromBackend(bv))
          else None
      }
    }
    trait LowerPriorityImplicits {
      def converterAsWriter[T](c: Converter[T, Any]): Writer[T]
      def converterAsReader[U](c: Converter[Nothing, U]): Reader[U]

      implicit def converterAsReadWriter[T](c: Converter[T, T]): ReadWriter[T] =
        ReadWriter[T](converterAsReader(c), converterAsWriter(c))
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

      implicit class ConverterHListOps[L, T](c: Converter[T, L]) {
        def >>>[F, V](f: F)(implicit ev: L <:< HList, tp: FnToProduct.Aux[F, L => V]): Converter[T, V] =
          this andThenUnpacked f
        def andThenUnpacked[F, V](f: F)(implicit ev: L <:< HList, tp: FnToProduct.Aux[F, L => V]): Converter[T, V] =
          c >> tp(f)
      }

      implicit def converterAsWriter[T](c: Converter[T, Any]): Writer[T] =
        Writer(c.toBackend)

      implicit def converterAsReader[U](c: Converter[Nothing, U]): Reader[U] =
        Reader {
          case bv if c.isDefinedAt(bv) => c.fromBackend(bv)
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
      type In <: HList
      type Out <: HList

      def toBackend(in: In, cc: CC, bo: backend.BObject): backend.BObject
      def isDefinedAt(cc: CC, bo: backend.BObject): Boolean
      def fromBackend(cc: CC, bo: backend.BObject): Option[Out]
    }
    object ObjectMapping {
      type Aux[CC <: HList, In0 <: HList, Out0 <: HList] = ObjectMapping[CC] { type In = In0; type Out = Out0 }

      implicit val hlistObjectMapping: Aux[HNil, HNil, HNil] = new ObjectMapping[HNil] {
        override type In = HNil
        override type Out = HNil

        override def toBackend(in: HNil, cc: HNil, bo: backend.BObject): backend.BObject = bo
        override def isDefinedAt(cc: HNil, bo: backend.BObject): Boolean = true
        override def fromBackend(cc: HNil, bo: backend.BObject): Option[HNil] = Some(HNil)
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

          override def isDefinedAt(cc: (String, Converter[T, U]) :: CS, bo: backend.BObject): Boolean = cc match {
            case (k, c) :: cs => backend.getObjectKey(bo, k).exists(c.isDefinedAt) && tm.isDefinedAt(cs, bo)
          }

          override def fromBackend(s: (String, Converter[T, U]) :: CS, bo: backend.BObject): Option[U :: US] = s match {
            case (k, c) :: cc =>
              for {
                v <- backend.getObjectKey(bo, k)
                mv <- c.lift.fromBackend(v)
                mt <- tm.fromBackend(cc, bo)
              } yield mv :: mt
          }
        }
    }
    
    object obj {
      def apply[CC <: HList](converters: CC)(implicit m: ObjectMapping[CC]): Converter[m.In, m.Out] =
        new Converter[m.In, m.Out] {
          override def toBackend(v: m.In): backend.BValue =
            m.toBackend(v, converters, backend.makeEmptyObject)

          override def isDefinedAt(bv: backend.BValue): Boolean = bv match {
            case backend.Get.Object(bo) => m.isDefinedAt(converters, bo)
            case _ => false
          }

          override def fromBackend(bv: backend.BValue): m.Out = bv match {
            case backend.Get.Object(bo) => m.fromBackend(converters, bo).get
          }
        }

      def as[M[A, B] <: Map[A, B]] = new AsBuilder[M]

      class AsBuilder[M[A, B] <: Map[A, B]] {
        def to[V](conv: Converter.Id[V])(implicit cbf: CanBuildFrom[M[String, V], (String, V), M[String, V]]): Converter.Id[M[String, V]] =
          Converter[M[String, V], M[String, V]](m => backend.makeObject(m.mapValues(conv.toBackend))) {
            case backend.Extract.Object(obj) if obj.values.forall(conv.isDefinedAt) =>
              val b = cbf()
              b ++= obj.mapValues(conv.fromBackend)
              b.result()
          }
      }
    }

    trait ArrayMapping[CC <: HList] {
      type In
      type Out

      def toBackend(in: In, cc: CC, ba: backend.BArray): backend.BArray
      def isDefinedAt(cc: CC, ba: backend.BArray, idx: Int): Boolean
      def fromBackend(cc: CC, ba: backend.BArray, idx: Int): Option[Out]
    }
    object ArrayMapping {
      type Aux[CC <: HList, In0 <: HList, Out0 <: HList] = ArrayMapping[CC] { type In = In0; type Out = Out0 }

      implicit val hnilArrayMapping: Aux[HNil, HNil, HNil] = new ArrayMapping[HNil] {
        override type In = HNil
        override type Out = HNil

        override def toBackend(in: HNil, cc: HNil, ba: backend.BArray): backend.BArray = ba
        override def isDefinedAt(cc: HNil, ba: backend.BArray, idx: Int): Boolean = true
        override def fromBackend(cc: HNil, ba: backend.BArray, idx: Int): Option[HNil] = Some(HNil)
      }

      implicit def hconsArrayMapping[T, U, CS <: HList, TS <: HList, US <: HList](implicit tm: Aux[CS, TS, US])
          : Aux[Converter[T, U] :: CS, T :: TS, U :: US] =
        new ArrayMapping[Converter[T, U] :: CS] {
          override type In = T :: TS
          override type Out = U :: US

          override def toBackend(in: T :: TS, cc: Converter[T, U] :: CS,
                                 ba: backend.BArray): backend.BArray = (in, cc) match {
            case (t :: ts, c :: cs) =>
              val nba = backend.pushToArray(ba, c.toBackend(t))
              tm.toBackend(ts, cs, nba)
          }

          override def isDefinedAt(cc: Converter[T, U] :: CS, ba: backend.BArray, idx: Int): Boolean = cc match {
            case c :: cs if idx < backend.getArrayLength(ba) =>
              c.isDefinedAt(backend.getArrayValueAt(ba, idx)) && tm.isDefinedAt(cs, ba, idx + 1)
            case _ => false
          }

          override def fromBackend(cc: Converter[T, U] :: CS, ba: backend.BArray, idx: Int): Option[U :: US] = cc match {
            case c :: cs if idx < backend.getArrayLength(ba) =>
              val bv = backend.getArrayValueAt(ba, idx)
              for {
                mv <- c.lift.fromBackend(bv)
                mt <- tm.fromBackend(cs, ba, idx+1)
              } yield mv :: mt
            case _ => None
          }
        }
    }

    object arr {
      def apply[CC <: HList](converters: CC)(implicit m: ArrayMapping[CC]): Converter[m.In, m.Out] =
        new Converter[m.In, m.Out] {
          override def toBackend(v: m.In) = m.toBackend(v, converters, backend.makeEmptyArray)

          override def isDefinedAt(bv: backend.BValue) = bv match {
            case backend.Get.Array(ba) => m.isDefinedAt(converters, ba, 0)
            case _ => false
          }

          override def fromBackend(bv: backend.BValue) = bv match {
            case backend.Get.Array(ba) => m.fromBackend(converters, ba, 0).get
          }
        }

      def as[C[T] <: Traversable[T]] = new AsBuilder[C]

      class AsBuilder[C[T] <: Traversable[T]] {
        def of[U](conv: Converter.Id[U])(implicit cbf: CanBuildFrom[C[U], U, C[U]]): Converter.Id[C[U]] =
          Converter[C[U], C[U]](c => backend.makeArray(c.toVector.map(conv.toBackend)(breakOut))) {
            case backend.Extract.Array(arr) if arr.forall(conv.isDefinedAt) =>
              arr.map[U, C[U]](conv.fromBackend)(breakOut)
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

      def >>[U](g: T => U): V => U = v => g(f(v))
    }

    implicit class ConverterProductFunctionOps[V, P <: Product](f: V => P) {
      def >>>[U, L <: HList](c: Converter[L, U])(implicit gen: Generic.Aux[P, L]): Converter[V, U] =
        new Converter[V, U] {
          def isDefinedAt(bv: backend.BValue): Boolean = c.isDefinedAt(bv)
          def fromBackend(bv: backend.BValue): U = c.fromBackend(bv)
          def toBackend(v: V): backend.BValue = c.toBackend(gen.to(f(v)))
        }
    }

    implicit class NumberConverterExt[U](m: Converter.Id[Number]) {
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
