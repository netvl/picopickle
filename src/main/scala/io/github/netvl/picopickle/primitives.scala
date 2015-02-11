package io.github.netvl.picopickle

trait PrimitiveWritersComponent {
  this: BackendComponent with TypesComponent =>

  implicit val intWriter: Writer[Int] = Writer.fromPF {
    case (n, None) => backend.makeNumber(n)
  }

  implicit val stringWriter: Writer[String] = Writer.fromPF {
    case (s, None) => backend.makeString(s)
  }

  implicit def optionWriter[T](implicit w: Writer[T]): Writer[Option[T]] = Writer.fromPF {
    case (Some(value), None) => backend.makeArray(Vector(w.write(value)))
    case (None, None) => backend.makeArray(Vector.empty)
  }
}

trait PrimitiveReadersComponent {
  this: BackendComponent with TypesComponent =>

  implicit val intReader: Reader[Int] = Reader {
    case backend.Extractors.Number(n) => backend.fromNumber(n).intValue()
  }

  implicit val stringReader: Reader[String] = Reader {
    case backend.Extractors.String(s) => backend.fromString(s)
  }
}

trait PrimitiveReaderWritersComponent extends PrimitiveReadersComponent with PrimitiveWritersComponent {
  this: BackendComponent with TypesComponent =>
}
