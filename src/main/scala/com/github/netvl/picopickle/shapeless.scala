package com.github.netvl.picopickle

import shapeless._
import shapeless.labelled._

trait LowerPriorityShapelessWriters {
  implicit def fieldTypeWriter[K <: Symbol, V](implicit kw: Witness.Aux[K], vw: Writer[V]): Writer[FieldType[K, V]] =
    new Writer[FieldType[K, V]] {
      override def write0(implicit b: Backend): (FieldType[K, V], Option[b.BValue]) => Option[b.BValue] =
        (f, v) => (f, v) match {
          case (f, Some(b.Extractors.Object(v))) => Some(b.setObjectKey(v, kw.value.name, vw.write0(b)(f, None).get))
          case (f, None) => Some(b.makeObject(Map(kw.value.name -> vw.write0(b)(f, None).get)))
        }
    }
}

trait ShapelessWriters extends LowerPriorityShapelessWriters {
  implicit def optionFieldTypeWriter[K <: Symbol, V](implicit kw: Witness.Aux[K], vw: Writer[V]): Writer[FieldType[K, Option[V]]] =
    new Writer[FieldType[K, Option[V]]] {
      override def write0(implicit b: Backend): (FieldType[K, Option[V]], Option[b.BValue]) => Option[b.BValue] =
        (f, v) => (f, v) match {
          case (f, Some(b.Extractors.Object(v))) => (f: Option[V]) match {
            case Some(value) => Some(b.setObjectKey(v, kw.value.name, vw.write0(b)(value, None).get))
            case None => Some(v)
          }
          case (f, None) => (f: Option[V]) match {
            case Some(value) => Some(b.makeObject(Map(kw.value.name -> vw.write0(b)(value, None).get)))
            case None => None
          }
        }
    }

  implicit def recordHeadWriter[H, T <: HList](implicit hw: Writer[H], tw: Writer[T],
                                               ev: H <:< FieldType[_, _]): Writer[H :: T] =
  new Writer[H :: T] {
    override def write0(implicit b: Backend): (H :: T, Option[b.BValue]) => Option[b.BValue] =
      (f, v) => (f, v) match {
        case (h :: t, bv) => tw.write0(b)(t, hw.write0(b)(h, bv))
      }
  }

  implicit val hnilWriter: Writer[HNil] = new Writer[HNil] {
    override def write0(implicit b: Backend): (HNil, Option[b.BValue]) => Option[b.BValue] = (_, v) => v
  }

  implicit def genericWriter[T, R](implicit g: LabelledGeneric.Aux[T, R], rw: Writer[R]): Writer[T] =
    new Writer[T] {
      override def write0(implicit b: Backend): (T, Option[b.BValue]) => Option[b.BValue] =
        (f, v) => rw.write0(b)(g.to(f), v)
    }
}

trait LowerPriorityShapelessReaders {
  implicit def fieldTypeReader[K <: Symbol, V](implicit kw: Witness.Aux[K], vr: Reader[V]): Reader[FieldType[K, V]] =
    new Reader[FieldType[K, V]] {
      override def read(implicit b: Backend): (b.BValue) => FieldType[K, V] = {
        case b.Extractors.Object(v) => field[K](vr.read(b)(b.getObjectKey(v, kw.value.name).get))
      }
    }
}

trait ShapelessReaders extends LowerPriorityShapelessReaders {
  implicit def optionFieldTypeReader[K <: Symbol, V](implicit kw: Witness.Aux[K], vr: Reader[V]): Reader[FieldType[K, Option[V]]] =
    new Reader[FieldType[K, Option[V]]] {
      override def read(implicit b: Backend): (b.BValue) => FieldType[K, Option[V]] = {
        case b.Extractors.Object(v) => field[K](b.getObjectKey(v, kw.value.name).map(vr.read(b)))
      }
    }

  implicit def recordHeadReader[H, T <: HList](implicit hr: Reader[H], tr: Reader[T],
                                               ev: H <:< FieldType[_, _]): Reader[H :: T] =
    new Reader[H :: T] {
      override def read(implicit b: Backend): (b.BValue) => H :: T = {
        case bv@b.Extractors.Object(_) => hr.read(b)(bv) :: tr.read(b)(bv)
      }
    }

  implicit val hnilReader: Reader[HNil] =
    new Reader[HNil] {
      override def read(implicit b: Backend): (b.BValue) => HNil = _ => HNil
    }

  implicit def genericReader[T, R](implicit g: LabelledGeneric.Aux[T, R], rr: Reader[R]): Reader[T] =
    new Reader[T] {
      override def read(implicit b: Backend): (b.BValue) => T = bv => g.from(rr.read(b)(bv))
    }
}

trait ShapelessReaderWriters extends ShapelessReaders with ShapelessWriters
