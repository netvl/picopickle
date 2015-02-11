package io.github.netvl.picopickle

import shapeless._
import shapeless.labelled._

trait LowerPriorityShapelessWriters2 {
  this: BackendComponent with TypesComponent =>
  implicit def genericProductWriter[T <: Product, R](implicit g: LabelledGeneric.Aux[T, R], rw: Lazy[Writer[R]]): Writer[T] =
    Writer.fromPF {
      case (f, v) => rw.value.write0(g.to(f), v)
    }
}

trait LowerPriorityShapelessWriters extends LowerPriorityShapelessWriters2 {
  this: BackendComponent with TypesComponent =>

  implicit def fieldTypeWriter[K <: Symbol, V](implicit kw: Witness.Aux[K], vw: Writer[V]): Writer[FieldType[K, V]] =
    Writer { f => {
      case Some(backend.Extractors.Object(v)) => backend.setObjectKey(v, kw.value.name, vw.write(f))
      case None => backend.makeObject(Map(kw.value.name -> vw.write(f)))
    }}
}

trait ShapelessWriters extends LowerPriorityShapelessWriters {
  this: BackendComponent with TypesComponent =>

  implicit def optionFieldTypeWriter[K <: Symbol, V](implicit kw: Witness.Aux[K], vw: Writer[V]): Writer[FieldType[K, Option[V]]] =
  Writer { f => {
    case Some(backend.Extractors.Object(v)) => (f: Option[V]) match {
      case Some(value) => backend.setObjectKey(v, kw.value.name, vw.write(value))
      case None => v: backend.BValue
    }
    case None => (f: Option[V]) match {
      case Some(value) => backend.makeObject(Map(kw.value.name -> vw.write(value)))
      case None => backend.makeEmptyObject
    }
  }}

  implicit def recordHeadWriter[H, T <: HList](implicit hw: Writer[H], tw: Writer[T],
                                               ev: H <:< FieldType[_, _]): Writer[H :: T] =
  Writer.fromPF {
    case (h :: t, bv) => tw.write0(t, Some(hw.write0(h, bv)))
  }

  implicit val hnilWriter: Writer[HNil] =
  new Writer[HNil] {
    override def write0(value: HNil, acc: Option[backend.BValue]): backend.BValue = acc match {
      case Some(bv) => bv
      case None => backend.makeEmptyObject
    }
  }
}

trait LowerPriorityShapelessReaders2 {
  this: BackendComponent with TypesComponent =>

  implicit def genericProductReader[T <: Product, R](implicit g: LabelledGeneric.Aux[T, R], rr: Lazy[Reader[R]]): Reader[T] =
    Reader { case bv => g.from(rr.value.read(bv)) }
}

trait LowerPriorityShapelessReaders extends LowerPriorityShapelessReaders2 {
  this: BackendComponent with TypesComponent =>
  
  implicit def fieldTypeReader[K <: Symbol, V](implicit kw: Witness.Aux[K], vr: Reader[V]): Reader[FieldType[K, V]] =
    Reader {
      case backend.Extractors.Object(v) => field[K](vr.read(backend.getObjectKey(v, kw.value.name).get))
    }
}

trait ShapelessReaders extends LowerPriorityShapelessReaders {
  this: BackendComponent with TypesComponent =>
  
  implicit def optionFieldTypeReader[K <: Symbol, V](implicit kw: Witness.Aux[K], vr: Reader[V]): Reader[FieldType[K, Option[V]]] =
    Reader {
      case backend.Extractors.Object(v) => field[K](backend.getObjectKey(v, kw.value.name).map(vr.read))
    }

  implicit def recordHeadReader[H, T <: HList](implicit hr: Reader[H], tr: Reader[T],
                                               ev: H <:< FieldType[_, _]): Reader[H :: T] =
    Reader {
      case bv@backend.Extractors.Object(_) => hr.read(bv) :: tr.read(bv)
    }

  implicit val hnilReader: Reader[HNil] = Reader { case _ => HNil }
}

trait ShapelessReaderWritersComponent extends ShapelessReaders with ShapelessWriters {
  this: BackendComponent with TypesComponent =>
}
