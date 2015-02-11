package io.github.netvl.picopickle

trait Tuple2Reader {
  this: BackendComponent with TypesComponent =>
  implicit def tuple2Reader[T1, T2](implicit r1: Reader[T1], r2: Reader[T2]): Reader[Tuple2[T1, T2]]
}

trait Tuple2Writer {
  this: BackendComponent with TypesComponent =>
  implicit def tuple2Writer[T1, T2](implicit w1: Writer[T1], w2: Writer[T2]): Writer[Tuple2[T1, T2]]
}

trait TupleReaders extends Tuple2Reader {
  this: BackendComponent with TypesComponent =>

  implicit def tuple2Reader[T1, T2](implicit r1: Reader[T1], r2: Reader[T2]): Reader[Tuple2[T1, T2]] =
    Reader {
      case backend.Extractors.Array(backend.From.Array(Vector(x1, x2))) =>
        Tuple2(r1.read(x1), r2.read(x2))
    }
}

trait TupleWriters extends Tuple2Writer {
  this: BackendComponent with TypesComponent =>

  implicit def tuple2Writer[T1, T2](implicit w1: Writer[T1], w2: Writer[T2]): Writer[Tuple2[T1, T2]] =
    Writer.fromPF {
      case (Tuple2(x1, x2), None) => backend.makeArray(Vector(w1.write(x1), w2.write(x2)))
    }
}

trait TupleReaderWritersComponent extends TupleReaders with TupleWriters {
  this: BackendComponent with TypesComponent =>
}