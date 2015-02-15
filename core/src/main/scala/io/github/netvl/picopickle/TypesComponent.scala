package io.github.netvl.picopickle

import scala.annotation.implicitNotFound

trait TypesComponent {
  this: BackendComponent =>

  @implicitNotFound("Don't know how to write ${T}; make sure that an implicit `Writer[${T}]` is in scope")
  trait Writer[T] {
    def write0(value: T, acc: Option[backend.BValue]): backend.BValue
    final def write(value: T): backend.BValue = write0(value, None)
  }

  object Writer {
    def fromPF0[T](ff: T => PartialFunction[Option[backend.BValue], backend.BValue]) =
      new Writer[T] {
        override def write0(value: T, acc: Option[backend.BValue]): backend.BValue =
          ff(value)(acc)
      }

    def fromPF1[T](ff: PartialFunction[(T, Option[backend.BValue]), backend.BValue]) =
      new Writer[T] {
        override def write0(value: T, acc: Option[backend.BValue]): backend.BValue =
          ff(value -> acc)
      }
    
    def apply[T](ff: PartialFunction[T, backend.BValue]) =
      new Writer[T] {
        override def write0(value: T, acc: Option[backend.BValue]): backend.BValue = 
          ff(value)
      }
  }

  @implicitNotFound("Don't know how to read ${T}; make sure that an implicit `Reader[${T}]` is in scope")
  trait Reader[T] {
    def read(value: backend.BValue): T
  }

  object Reader {
    def apply[T](f: PartialFunction[backend.BValue, T]) =
      new Reader[T] {
        override def read(value: backend.BValue): T = f(value)
      }
  }
}

