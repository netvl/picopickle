package io.github.netvl.picopickle

import scala.annotation.implicitNotFound

trait TypesComponent { this: BackendComponent =>
  final type PF[-A, +B] = PartialFunction[A, B]

  @implicitNotFound("Don't know how to write ${T}; make sure that an implicit `Writer[${T}]` is in scope")
  trait Writer[T] {
    def write0(value: T, acc: Option[backend.BValue]): backend.BValue
    final def write(value: T): backend.BValue = write0(value, None)
  }

  object Writer {
    def fromPF0[T](ff: T => PF[Option[backend.BValue], backend.BValue]) =
      new Writer[T] {
        override def write0(value: T, acc: Option[backend.BValue]): backend.BValue =
          ff(value)(acc)
      }

    def fromPF1[T](ff: PF[(T, Option[backend.BValue]), backend.BValue]) =
      new Writer[T] {
        override def write0(value: T, acc: Option[backend.BValue]): backend.BValue =
          ff(value -> acc)
      }
    
    def apply[T](ff: PF[T, backend.BValue]) =
      new Writer[T] {
        override def write0(value: T, acc: Option[backend.BValue]): backend.BValue =
          ff(value)
      }
  }

  @implicitNotFound("Don't know how to read ${T}; make sure that an implicit `Reader[${T}]` is in scope")
  trait Reader[T] { source =>
    def canRead(value: backend.BValue): Boolean = true
    def read(value: backend.BValue): T

    final def orElse(other: Reader[T]): Reader[T] = new Reader[T] {
      override def canRead(value: backend.BValue) =
        source.canRead(value) || other.canRead(value)
      override def read(value: backend.BValue): T =
        if (source.canRead(value)) source.read(value) else other.read(value)
    }

    final def orElse(other: PF[backend.BValue, T]): Reader[T] = this orElse Reader(other)

    final def andThen[U](f: T => U): Reader[U] = new Reader[U] {
      override def canRead(value: backend.BValue) = source.canRead(value)
      override def read(value: backend.BValue): U = f(source.read(value))
    }
  }

  object Reader {
    def apply[T](f: PF[backend.BValue, T]) =
      new Reader[T] {
        override def canRead(value: backend.BValue) =  f.isDefinedAt(value)
        override def read(value: backend.BValue): T = f(value)
      }
  }

  type ReadWriter[T] = Reader[T] with Writer[T]

  object ReadWriter {
    def reading[T](rf: PF[backend.BValue, T]) = new WriterBuilder(rf)
    def writing[T](wf: PF[T, backend.BValue]) = new ReaderBuilder(wf)
    
    class WriterBuilder[T](rf: PF[backend.BValue, T]) {
      def writing(wf: PF[T, backend.BValue]): ReadWriter[T] = new PfReadWriter[T](rf, wf)
    }

    class ReaderBuilder[T](wf: PF[T, backend.BValue]) {
      def reading(rf: PF[backend.BValue, T]): ReadWriter[T] = new PfReadWriter[T](rf, wf)
    }

    private class PfReadWriter[T](rf: PF[backend.BValue, T], wf: PF[T, backend.BValue]) extends Reader[T] with Writer[T] {
      override def canRead(value: backend.BValue) = rf.isDefinedAt(value)
      override def read(value: backend.BValue) = rf(value)

      override def write0(value: T, acc: Option[backend.BValue]) = wf(value)
    }
  }
}

