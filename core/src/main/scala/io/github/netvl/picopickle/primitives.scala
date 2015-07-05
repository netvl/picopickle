package io.github.netvl.picopickle

trait PrimitiveWriters {
  this: BackendComponent with TypesComponent =>

  implicit val unitWriter: Writer[Unit] = Writer {
    case _ => backend.makeEmptyObject
  }

  protected final def numWriter[T](implicit asNumber: T => Number): Writer[T] = Writer {
    case x => backend.makeNumberAccurately(asNumber(x))
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
  implicit def someWriter[T: Writer]: Writer[Some[T]] = Writer {
    case s => optionWriter[T].write(s)
  }
  implicit val noneWriter: Writer[None.type] = Writer {
    case None => optionWriter[Int].write(None)
  }

  implicit def eitherWriter[A, B](implicit wa: Writer[A], wb: Writer[B]): Writer[Either[A, B]] = Writer {
    case Left(l) => backend.makeArray(Vector(backend.makeNumber(0), wa.write(l)))
    case Right(r) => backend.makeArray(Vector(backend.makeNumber(1), wb.write(r)))
  }
  implicit def leftWriter[A: Writer, B: Writer]: Writer[Left[A, B]] = Writer {
    case l => eitherWriter[A, B].write(l)
  }
  implicit def rightWriter[A: Writer, B: Writer]: Writer[Right[A, B]] = Writer {
    case r => eitherWriter[A, B].write(r)
  }

  implicit val symbolWriter: Writer[Symbol] = Writer {
    case s => backend.makeString(s.name)
  }

  implicit val nullWriter: Writer[Null] = Writer {
    case null => backend.makeNull
  }
}

trait PrimitiveReaders {
  this: BackendComponent with TypesComponent with ExceptionsComponent =>

  // basic primitives

  implicit val unitReader: Reader[Unit] = Reader.reading {
    case backend.Extract.Object(m) if m.isEmpty => ()
  }.orThrowing(whenReading = "unit", expected = "empty object")

  protected final def numReader[T](name: String, f: Number => T): Reader[T] = Reader.reading {
    case n if backend.fromNumberAccurately.isDefinedAt(n) =>
      f(backend.fromNumberAccurately(n))
  }.orThrowing(whenReading = name, expected = backend.fromNumberAccuratelyExpected)

  implicit val byteReader: Reader[Byte] = numReader("byte", _.byteValue())
  implicit val shortReader: Reader[Short] = numReader("short", _.shortValue())
  implicit val intReader: Reader[Int] = numReader("int", _.intValue())
  implicit val longReader: Reader[Long] = numReader("long", _.longValue())
  implicit val floatReader: Reader[Float] = numReader("float", _.floatValue())
  implicit val doubleReader: Reader[Double] = numReader("double", _.doubleValue())

  implicit val charReader: Reader[Char] = Reader.reading {
    case backend.Extract.String(s) => s.charAt(0)
  }.orThrowing(whenReading = "char", expected = "string")

  implicit val booleanReader: Reader[Boolean] = Reader.reading {
    case backend.Extract.Boolean(b) => b
  }.orThrowing(whenReading = "boolean", expected = "boolean")

  implicit val stringReader: Reader[String] = Reader.reading {
    case backend.Extract.String(s) => s
  }.orThrowing(whenReading = "string", expected = "string")

  // option

  implicit def optionReader[T](implicit r: Reader[T]): Reader[Option[T]] = Reader.reading {
    case backend.Extract.Array(arr) if arr.length <= 1 => arr.headOption.map(r.read)
  }.orThrowing(whenReading = "option", expected = "array")

  implicit def someReader[T](implicit r: Reader[T]): Reader[Some[T]] = Reader.reading {
    case backend.Extract.Array(arr) if arr.length == 1 => Some(r.read(arr.head))
  }.orThrowing(whenReading = "some", expected = "array with one element")

  implicit val noneReader: Reader[None.type] = Reader.reading {
    case backend.Extract.Array(arr) if arr.isEmpty => None
  }.orThrowing(whenReading = "none", expected = "empty array")

  // either

  implicit def eitherReader[A, B](implicit ra: Reader[A], rb: Reader[B]): Reader[Either[A, B]] = Reader.reading[Either[A, B]] {
    case backend.Extract.Array(Vector(backend.Extract.Number(n), bv)) if n.intValue() == 0 =>
      Left(ra.read(bv))
    case backend.Extract.Array(Vector(backend.Extract.Number(n), bv)) if n.intValue() == 1 =>
      Right(rb.read(bv))
  }.orThrowing(whenReading = "either", expected = "array with first element 0 or 1")

  implicit def leftReader[A, B](implicit ra: Reader[A]): Reader[Left[A, B]] = Reader.reading[Left[A, B]] {
    case backend.Extract.Array(Vector(backend.Extract.Number(n), bv)) if n.intValue() == 0 =>
      Left(ra.read(bv))
  }.orThrowing(whenReading = "left", expected = "array with first element 0")

  implicit def rightReader[A, B](implicit rb: Reader[B]): Reader[Right[A, B]] = Reader.reading[Right[A, B]] {
    case backend.Extract.Array(Vector(backend.Extract.Number(n), bv)) if n.intValue() == 1 =>
      Right(rb.read(bv))
  }.orThrowing(whenReading = "right", expected = "array with first element 1")

  implicit val symbolReader: Reader[Symbol] = Reader.reading {
    case backend.Extract.String(s) => Symbol(s)
  }.orThrowing(whenReading = "symbol", expected = "string")

  implicit val nullReader: Reader[Null] = Reader.reading {
    case backend.Get.Null(_) => null
  }.orThrowing(whenReading = "null", expected = "null")
}

trait PrimitiveReaderWritersComponent extends PrimitiveReaders with PrimitiveWriters {
  this: BackendComponent with TypesComponent with ExceptionsComponent =>
}
