package io.github.netvl.picopickle

trait PrimitiveWritersComponent {
  this: BackendComponent with TypesComponent =>

  protected final val MaxLongInDouble: Long = 1L << 53  // Double has 53 bits of precision
  
  protected final def numWriter[T]: Writer[T] = Writer {
    case x @ Double.PositiveInfinity => backend.makeString(x.toString)
    case x @ Double.NegativeInfinity => backend.makeString(x.toString)
    case x: Double if x.isNaN => backend.makeString(x.toString)
    case x @ Float.PositiveInfinity => backend.makeString(x.toString)
    case x @ Float.NegativeInfinity => backend.makeString(x.toString)
    case x: Float if x.isNaN => backend.makeString(x.toString)
    // those longs which do not fit into double
    case x: Long if x.abs > MaxLongInDouble => backend.makeString(x.toString)
    case x: Number => backend.makeNumber(x)
  }

  implicit val byteWriter: Writer[Byte] = numWriter[Byte]
  implicit val shortWriter: Writer[Short] = numWriter[Short]
  implicit val intWriter: Writer[Int] = numWriter[Int]
  implicit val longWriter: Writer[Long] = numWriter[Long]
  implicit val floatWriter: Writer[Float] = numWriter[Float]
  implicit val doubleWriter: Writer[Double] = numWriter[Double]

  implicit val charWriter: Writer[Char] = Writer {
    case c => backend.makeString(c.toString)
  }

  implicit val booleanWriter: Writer[Boolean] = Writer {
    case b => backend.makeBoolean(b)
  }

  implicit val stringWriter: Writer[String] = Writer {
    case s => backend.makeString(s)
  }

  implicit def optionWriter[T](implicit w: Writer[T]): Writer[Option[T]] = Writer {
    case Some(value) => backend.makeArray(Vector(w.write(value)))
    case None => backend.makeArray(Vector.empty)
  }

  implicit val symbolWriter: Writer[Symbol] = Writer {
    case s => backend.makeString(s.name)
  }
}

trait PrimitiveReadersComponent {
  this: BackendComponent with TypesComponent =>

  protected final def numReader[T](f: Number => T): Reader[T] = Reader {
    case backend.Extract.Number(n) => f(n)
    case backend.Extract.String(s)
      if s == Double.PositiveInfinity.toString ||
        s == Double.NegativeInfinity.toString ||
        s == Double.NaN.toString => f(s.toDouble)
    case backend.Extract.String(s) => f(s.toLong)  // handles big longs
  }

  implicit val byteReader: Reader[Byte] = numReader(_.byteValue())
  implicit val shortReader: Reader[Short] = numReader(_.shortValue())
  implicit val intReader: Reader[Int] = numReader(_.intValue())
  implicit val longReader: Reader[Long] = numReader(_.longValue())
  implicit val floatReader: Reader[Float] = numReader(_.floatValue())
  implicit val doubleReader: Reader[Double] = numReader(_.doubleValue())

  implicit val charReader: Reader[Char] = Reader {
    case backend.Extract.String(s) => s.charAt(0)
  }

  implicit val booleanReader: Reader[Boolean] = Reader {
    case backend.Extract.Boolean(b) => b
  }

  implicit val stringReader: Reader[String] = Reader {
    case backend.Extract.String(s) => s
  }

  implicit def optionReader[T](implicit r: Reader[T]): Reader[Option[T]] = Reader {
    case backend.Extract.Array(Vector(v)) => Some(r.read(v))
    case backend.Extract.Array(Vector()) => None
  }

  implicit val symbolReader: Reader[Symbol] = Reader {
    case backend.Extract.String(s) => Symbol(s)
  }
}

trait PrimitiveReaderWritersComponent extends PrimitiveReadersComponent with PrimitiveWritersComponent {
  this: BackendComponent with TypesComponent =>
}
