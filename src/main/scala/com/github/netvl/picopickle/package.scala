package com.github.netvl

package object picopickle extends ShapelessReaderWriters with PrimitiveReaderWriters {
  object decorators {
    implicit class WriteDecorator[T: Writer](value: T)(implicit val b: Backend) {
      def write: b.BValue = implicitly[Writer[T]].write(b)(value).get
    }

    implicit class ReadDecorator[BV](bv: BV)(implicit b: Backend) {
      def read[T: Reader](implicit ev: BV <:< b.BValue) = implicitly[Reader[T]].read(b)(ev(bv))
    }
  }
}
