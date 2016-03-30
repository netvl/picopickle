package io.github.netvl.picopickle

import io.github.netvl.picopickle.backends.collections.CollectionsPickler
import shapeless._
import shapeless.ops.hlist.IsHCons

trait ValueClassReaders { this: TypesComponent ⇒
  implicit def valueClassReader[
    ValueClass <: AnyVal,
    VCAsHList <: HList,
    Value
  ](implicit gen: Generic.Aux[ValueClass, VCAsHList],
    isHCons: IsHCons.Aux[VCAsHList, Value, HNil],
    valueReader: Reader[Value],
    eqv: (Value :: HNil) =:= VCAsHList): Reader[ValueClass] =
    valueReader.andThen { value ⇒
      gen.from(value :: HNil)
    }
}

trait ValueClassWriters { this: TypesComponent ⇒
  implicit def valueClassWriter[
    ValueClass <: AnyVal,
    VCAsHList <: HList,
    Value](implicit gen: Generic.Aux[ValueClass, VCAsHList],
           isHCons: IsHCons.Aux[VCAsHList, Value, HNil],
           valueWriter: Writer[Value]): Writer[ValueClass] =
    Writer(t ⇒ valueWriter.write(gen.to(t).head))
}

/* By default, a value class is pickled like any case class,
 * i.e. `MyValueClass("someValue")` becomes `Map("value" -> "someValue")`
 * if you mix in this trait, the above instance gets serialises as "someValue" (no Map)
 */
trait ValueClassesReaderWritersComponent extends ValueClassReaders with ValueClassWriters {
  this: BackendComponent with TypesComponent =>
}
