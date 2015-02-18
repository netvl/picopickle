package io.github.netvl.picopickle

import shapeless._
import shapeless.labelled._

trait SealedTraitDiscriminator {
  def discriminatorKey: String
}

trait DefaultSealedTraitDiscriminator extends SealedTraitDiscriminator {
  override val discriminatorKey: String = "$variant"
}

trait LowerPriorityShapelessWriters2 {
  this: BackendComponent with TypesComponent =>
  implicit def genericWriter[T, R](implicit g: LabelledGeneric.Aux[T, R], rw: Lazy[Writer[R]]): Writer[T] =
    Writer.fromPF1 {
      case (f, v) => rw.value.write0(g.to(f), v)
    }
}

trait LowerPriorityShapelessWriters extends LowerPriorityShapelessWriters2 {
  this: BackendComponent with TypesComponent =>

  implicit def fieldTypeWriter[K <: Symbol, V](implicit kw: Witness.Aux[K], vw: Writer[V]): Writer[FieldType[K, V]] =
    Writer.fromPF0 { f => {
      case Some(backend.Get.Object(v)) => backend.setObjectKey(v, kw.value.name, vw.write(f))
      case None => backend.makeObject(Map(kw.value.name -> vw.write(f)))
    }}
}

trait ShapelessWriters extends LowerPriorityShapelessWriters {
  this: BackendComponent with TypesComponent with SealedTraitDiscriminator =>

  implicit def optionFieldTypeWriter[K <: Symbol, V](implicit kw: Witness.Aux[K], vw: Writer[V]): Writer[FieldType[K, Option[V]]] =
    Writer.fromPF0 { f => {
      case Some(backend.Get.Object(v)) => (f: Option[V]) match {
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
    Writer.fromPF1 {
      case (h :: t, bv) => tw.write0(t, Some(hw.write0(h, bv)))
    }

  implicit val hnilWriter: Writer[HNil] =
    new Writer[HNil] {
      override def write0(value: HNil, acc: Option[backend.BValue]): backend.BValue = acc match {
        case Some(bv) => bv
        case None => backend.makeEmptyObject
      }
    }

  protected object ObjectOrEmpty {
    def unapply(bv: Option[backend.BValue]): Option[backend.BObject] = bv match {
      case Some(backend.Get.Object(obj)) => Some(obj)
      case None => Some(backend.makeEmptyObject)
      case _ => None
    }
  }
  
  implicit def coproductWriter[K <: Symbol, V, T <: Coproduct](implicit vw: Writer[V],
                                                               tw: Writer[T],
                                                               kw: Witness.Aux[K]): Writer[FieldType[K, V] :+: T] =
    Writer.fromPF1 {
      case (Inl(h), ObjectOrEmpty(obj)) =>
        vw.write0(h, Some(backend.setObjectKey(obj, discriminatorKey, backend.makeString(kw.value.name))))

      case (Inr(t), ObjectOrEmpty(obj)) =>
        tw.write0(t, Some(obj))
    }

  implicit val cnilWriter: Writer[CNil] =
    new Writer[CNil] {
      override def write0(value: CNil, acc: Option[backend.BValue]): backend.BValue = acc match {
        case Some(obj) => obj  // pass through the accumulated value
        // This is impossible, I believe
        case None => throw new IllegalStateException("Couldn't serialize a sealed trait")
      }
    }
}

trait LowerPriorityShapelessReaders2 {
  this: BackendComponent with TypesComponent =>

  implicit def genericReader[T, R](implicit g: LabelledGeneric.Aux[T, R], rr: Lazy[Reader[R]]): Reader[T] =
    Reader { case bv => g.from(rr.value.read(bv)) }
}

trait LowerPriorityShapelessReaders extends LowerPriorityShapelessReaders2 {
  this: BackendComponent with TypesComponent =>
  
  implicit def fieldTypeReader[K <: Symbol, V](implicit kw: Witness.Aux[K], vr: Reader[V]): Reader[FieldType[K, V]] =
    Reader {
      case backend.Get.Object(v) => field[K](vr.read(backend.getObjectKey(v, kw.value.name).get))
    }
}

trait ShapelessReaders extends LowerPriorityShapelessReaders {
  this: BackendComponent with TypesComponent with SealedTraitDiscriminator =>
  
  implicit def optionFieldTypeReader[K <: Symbol, V](implicit kw: Witness.Aux[K], vr: Reader[V]): Reader[FieldType[K, Option[V]]] =
    Reader {
      case backend.Get.Object(v) => field[K](backend.getObjectKey(v, kw.value.name).map(vr.read))
    }

  implicit def recordHeadReader[H, T <: HList](implicit hr: Reader[H], tr: Reader[T],
                                               ev: H <:< FieldType[_, _]): Reader[H :: T] =
    Reader {
      case bv@backend.Get.Object(_) => hr.read(bv) :: tr.read(bv)
    }

  implicit val hnilReader: Reader[HNil] = Reader { case _ => HNil }

  protected object ObjectWithDiscriminator {
    def unapply(value: backend.BValue): Option[String] =
      backend.Extract.Object.unapply(value)
        .flatMap(_.get(discriminatorKey))
        .flatMap(backend.Extract.String.unapply)
  }

  implicit def coproductReader[K <: Symbol, V, T <: Coproduct](implicit vr: Reader[V], tr: Reader[T],
                                                               kw: Witness.Aux[K]): Reader[FieldType[K, V] :+: T] =
    Reader {
      case bv@ObjectWithDiscriminator(key) =>
        if (key == kw.value.name) Inl[FieldType[K, V], T](field[K](vr.read(bv)))
        else Inr[FieldType[K, V], T](tr.read(bv))
    }

  implicit val cnilReader: Reader[CNil] = Reader {
    // TODO: come up with better exceptions
    case ObjectWithDiscriminator(key) =>
      throw new IllegalArgumentException(s"Unknown discriminator: $key")
    case _ =>
      throw new IllegalArgumentException(s"Discriminator is unavailable")
  }
}

trait ShapelessReaderWritersComponent extends ShapelessReaders with ShapelessWriters {
  this: BackendComponent with TypesComponent with SealedTraitDiscriminator =>
}
