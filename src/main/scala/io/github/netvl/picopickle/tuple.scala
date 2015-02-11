package io.github.netvl.picopickle

trait Tuple2Reader {
  this: BackendComponent with TypesComponent =>
  implicit def tuple2Reader[T1, T2](implicit r1: Reader[T1], r2: Reader[T2]): Reader[Tuple2[T1, T2]]
}

trait Tuple2Writer {
  this: BackendComponent with TypesComponent =>
  implicit def tuple2Writer[T1, T2](implicit w1: Writer[T1], w2: Writer[T2]): Writer[Tuple2[T1, T2]]
}
