package io.github.netvl.picopickle

import shapeless._
import shapeless.ops.hlist.IsHCons

trait ValueClassReaders {
  this: TypesComponent =>

  implicit def valueClassReader[T <: AnyVal, R <: HList, V](implicit gen: Generic.Aux[T, R],
                                                            isHCons: IsHCons.Aux[R, V, HNil],
                                                            ev: (V :: HNil) =:= R,
                                                            vr: Reader[V]): Reader[T] =
    vr.andThen { value => gen.from(value :: HNil) }
}

trait ValueClassWriters {
  this: TypesComponent =>

  implicit def valueClassWriter[T <: AnyVal, R <: HList, V](implicit gen: Generic.Aux[T, R],
                                                            isHCons: IsHCons.Aux[R, V, HNil],
                                                            vw: Writer[V]): Writer[T] =
    Writer(t ⇒ vw.write(gen.to(t).head))
}

trait ValueClassReaderWritersComponent extends ValueClassReaders with ValueClassWriters {
  this: TypesComponent =>
}
