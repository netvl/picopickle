package io.github.netvl.picopickle

import scala.language.implicitConversions

trait ConvertersComponent {
  this: BackendComponent with TypesComponent =>

  object converters {
    trait Converter[+T, -U] {
      self =>

      def isDefinedAt(bv: backend.BValue): Boolean
      def fromBackend(bv: backend.BValue): T
      def toBackend(v: U): backend.BValue

      final def >>[T1](f: T => T1): Converter[T1, U] = this andThen f
      final def andThen[T1](f: T => T1): Converter[T1, U] = new Converter[T1, U] {
        def isDefinedAt(bv: backend.BValue): Boolean = self.isDefinedAt(bv)
        def fromBackend(bv: backend.BValue): T1 = f(self.fromBackend(bv))
        def toBackend(v: U): backend.BValue = self.toBackend(v)
      }

      final def ||[T1 >: T](c: Converter[T1, Nothing]): Converter[T1, U] = this orElse c
      final def orElse[T1 >: T](c: Converter[T1, Nothing]): Converter[T1, U] = new Converter[T1, U] {
        def isDefinedAt(bv: backend.BValue): Boolean = self.isDefinedAt(bv) || c.isDefinedAt(bv)
        def fromBackend(bv: backend.BValue): T1 = bv match {
          case _ if self.isDefinedAt(bv) => self.fromBackend(bv)
          case _ if c.isDefinedAt(bv) => c.fromBackend(bv)
        }
        def toBackend(v: U): backend.BValue = self.toBackend(v)
      }
    }
    object Converter {
      type Id[T] = Converter[T, T]

      def onlyReader[T](from: PF[backend.BValue, T]): Converter[T, Nothing] =
        apply(from)(_ => throw new IllegalArgumentException)

      def onlyWriter[U](to: U => backend.BValue): Converter[Any, U] =
        apply { case _ => throw new IllegalArgumentException }(to)

      def apply[T, U](from: PF[backend.BValue, T])(to: U => backend.BValue): Converter[T, U] =
        new Converter[T, U] {
          def isDefinedAt(bv: backend.BValue): Boolean = from.isDefinedAt(bv)
          def fromBackend(bv: backend.BValue): T = from(bv)
          def toBackend(v: U): backend.BValue = to(v)
        }
    }

    val `null`: Converter.Id[Null] = Converter {
      case backend.Get.Null(_) => null
    }(_ => backend.makeNull)

    val bool: Converter.Id[Boolean] = Converter {
      case backend.Extract.Boolean(b) => b
    }(backend.makeBoolean)

    val num: Converter.Id[Number] = Converter {
      case backend.Extract.Number(n) => n
    }(backend.makeNumber)

    val str: Converter.Id[String] = Converter {
      case backend.Extract.String(s) => s
    }(backend.makeString)

    implicit def converterAsReadWriter[T](c: Converter[T, T]): ReadWriter[T] =
      ReadWriter[T](c, c)

    implicit def converterAsReader[T](c: Converter[T, Nothing]): Reader[T] =
      Reader {
        case bv if c.isDefinedAt(bv) => c.fromBackend(bv)
      }

    implicit def converterAsWriter[U](c: Converter[Any, U]): Writer[U] =
      Writer {
        case v => c.toBackend(v)
      }

    implicit class ConverterFunctionOps[V, U](f: V => U) {
      def >>[T](c: Converter[T, U]): Converter[T, V] =
        new Converter[T, V] {
          def isDefinedAt(bv: backend.BValue): Boolean = c.isDefinedAt(bv)
          def fromBackend(bv: backend.BValue): T = c.fromBackend(bv)
          def toBackend(v: V): backend.BValue = c.toBackend(f(v))
        }
    }
  }
}
