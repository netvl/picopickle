package io.github.netvl

package object picopickle extends ShapelessReaderWriters with PrimitiveReaderWriters {
  object functions {
    def write[T](value: T)(implicit w: Writer[T], b: Backend): b.BValue = w.write(b)(value).get

    class ReadInvocation(val b: Backend) extends AnyVal {
      def value[T](value: b.BValue)(implicit r: Reader[T]): T = r.read(b)(value)
    }

    def read(implicit b: Backend) = new ReadInvocation(b)
  }

  object decorators {
    implicit class WriteDecorator[T: Writer](value: T)(implicit val b: Backend) {
      def write: b.BValue = implicitly[Writer[T]].write(b)(value).get
    }

    implicit class ReadDecorator[BV](bv: BV)(implicit b: Backend) {
      def read[T: Reader](implicit ev: BV <:< b.BValue) = implicitly[Reader[T]].read(b)(ev(bv))
    }
  }
}
