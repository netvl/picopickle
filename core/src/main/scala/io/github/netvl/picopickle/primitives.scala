package io.github.netvl.picopickle

trait PrimitiveWritersComponent {
  this: BackendComponent with TypesComponent =>

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

trait PrimitiveReadersComponent {
  this: BackendComponent with TypesComponent =>

  protected final def numReader[T](f: Number => T): Reader[T] = Reader {
    case bv => f(backend.fromNumberAccurately(bv))
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
    case backend.Extract.Array(arr) => arr.headOption.map(r.read)
  }
  implicit def someReader[T: Reader]: Reader[Some[T]] = Reader {
    case bv => optionReader[T].read(bv).asInstanceOf[Some[T]]
  }
  implicit val noneReader: Reader[None.type] = Reader {
    case bv => optionReader[Int].read(bv).asInstanceOf[None.type]
  }

  implicit def eitherReader[A, B](implicit ra: Reader[A], rb: Reader[B]): Reader[Either[A, B]] = Reader {
    case backend.Extract.Array(Vector(backend.Extract.Number(n), bv)) if n.intValue() == 0 =>
      Left(ra.read(bv))
    case backend.Extract.Array(Vector(backend.Extract.Number(n), bv)) if n.intValue() == 1 =>
      Right(rb.read(bv))
  }
  implicit def leftReader[A: Reader, B: Reader]: Reader[Left[A, B]] = Reader {
    case bv => eitherReader[A, B].read(bv).asInstanceOf[Left[A, B]]
  }
  implicit def rightReader[A: Reader, B: Reader]: Reader[Right[A, B]] = Reader {
    case bv => eitherReader[A, B].read(bv).asInstanceOf[Right[A, B]]
  }

  implicit val symbolReader: Reader[Symbol] = Reader {
    case backend.Extract.String(s) => Symbol(s)
  }

  implicit val nullReader: Reader[Null] = Reader {
    case backend.Get.Null(_) => null
  }
}

trait PrimitiveReaderWritersComponent extends PrimitiveReadersComponent with PrimitiveWritersComponent {
  this: BackendComponent with TypesComponent =>
}
