package io.github.netvl.picopickle

import io.github.netvl.picopickle.SourceTypeTag.@@@
import shapeless._
import shapeless.labelled._

trait LowerPriorityShapelessWriters2 {
  this: BackendComponent with TypesComponent =>

  implicit def genericCoproductWriter[T, R <: Coproduct, RT <: Coproduct](implicit g: LabelledGeneric.Aux[T, R],
                                                                          rt: TagWithType.Aux[R, T, RT],
                                                                          wr: Lazy[Writer[RT]]): Writer[T] =
    Writer.fromF1 {
      case (v, bv) => wr.value.write0(rt.wrap(g.to(v)), bv)
    }

  implicit def genericHListWriter[T, R <: HList](implicit g: LabelledGeneric.Aux[T, R],
                                                 wr: Lazy[Writer[R]]): Writer[T] =
    Writer.fromF1 {
      case (f, bv) => wr.value.write0(g.to(f), bv)
    }
}

trait LowerPriorityShapelessWriters extends LowerPriorityShapelessWriters2 {
  this: BackendComponent with TypesComponent  =>

  implicit def fieldTypeWriter[K <: Symbol, V](implicit kw: Witness.Aux[K], vw: Lazy[Writer[V]]): Writer[FieldType[K, V]] =
    Writer.fromF0N { f => {
      case Some(backend.Get.Object(v)) => backend.setObjectKey(v, kw.value.name, vw.value.write(f))
      case None => backend.makeObject(Map(kw.value.name -> vw.value.write(f)))
    }}
}

trait ShapelessWriters extends LowerPriorityShapelessWriters {
  this: BackendComponent with TypesComponent with SealedTraitDiscriminatorComponent =>

  implicit def optionFieldTypeWriter[K <: Symbol, V](implicit kw: Witness.Aux[K],
                                                     vw: Lazy[Writer[V]]): Writer[FieldType[K, Option[V]]] =
    Writer.fromF0N { f => {
      case Some(backend.Get.Object(v)) => (f: Option[V]) match {
        case Some(value) => backend.setObjectKey(v, kw.value.name, vw.value.write(value))
        case None => v: backend.BValue
      }
      case None => (f: Option[V]) match {
        case Some(value) => backend.makeObject(Map(kw.value.name -> vw.value.write(value)))
        case None => backend.makeEmptyObject
      }
    } }

  implicit def recordHeadWriter[H, T <: HList](implicit hw: Lazy[Writer[H]], tw: Lazy[Writer[T]],
                                               ev: H <:< FieldType[_, _]): Writer[H :: T] =
    Writer.fromF1 {
      case (h :: t, bv) => tw.value.write0(t, Some(hw.value.write0(h, bv)))
    }

  implicit val hnilWriter: Writer[HNil] =
    Writer.fromF0N { _ => {
      case Some(bv) => bv
      case None => backend.makeEmptyObject
    } }

  protected object ObjectOrEmpty {
    def unapply(bv: Option[backend.BValue]): Option[backend.BObject] = bv match {
      case Some(backend.Get.Object(obj)) => Some(obj)
      case None => Some(backend.makeEmptyObject)
      case _ => None
    }
  }
  
  implicit def coproductWriter[K <: Symbol, V, U, R <: Coproduct](implicit vw: Lazy[Writer[V]],
                                                                  tw: Lazy[Writer[R]],
                                                                  discriminator: Discriminator.Aux[U] = null,
                                                                  kw: Witness.Aux[K]): Writer[(FieldType[K, V] @@@ U) :+: R] =
    Writer.fromF1 {
      case (Inl(h), ObjectOrEmpty(obj)) =>
        val dkey = Option(discriminator).flatMap(_.value).getOrElse(defaultDiscriminatorKey)
        vw.value.write0(h, Some(backend.setObjectKey(obj, dkey, backend.makeString(kw.value.name))))

      case (Inr(t), ObjectOrEmpty(obj)) =>
        tw.value.write0(t, Some(obj))
    }

  implicit val cnilWriter: Writer[CNil] =
    Writer.fromF0N { _ =>  {
      case Some(obj) => obj  // pass through the accumulated value
      // This is impossible, I believe
      case None => throw new IllegalStateException("Couldn't serialize a sealed trait")
    } }
}

trait LowerPriorityShapelessReaders2 {
  this: BackendComponent with TypesComponent =>

  implicit def genericReader[T, R, RT](implicit g: LabelledGeneric.Aux[T, R],
                                       rt: TagWithType.Aux[R, T, RT],
                                       rr: Lazy[Reader[RT]]): Reader[T] =
    rr.value.andThen(rt.unwrap _ andThen g.from)
}

trait LowerPriorityShapelessReaders extends LowerPriorityShapelessReaders2 {
  this: BackendComponent with TypesComponent with DefaultValuesComponent =>
  
  implicit def fieldTypeReaderTagged[K <: Symbol, V, T](implicit kw: Witness.Aux[K],
                                                        vr: Lazy[Reader[V]],
                                                        dv: DefaultValue.Aux[T, K, V]): Reader[FieldType[K, V] @@@ T] =
    Reader.reading {
      case backend.Get.Object(v) if backend.containsObjectKey(v, kw.value.name) || dv.value.isDefined =>
        val value = backend.getObjectKey(v, kw.value.name).map(vr.value.read).orElse(dv.value).get
        SourceTypeTag[T].attachTo(field[K](value))
    }.orThrowing(
        whenReading = s"case class field '${kw.value.name}'",
        expected = s"object with key '${kw.value.name}' or a default value for this field"
    )
}

trait ShapelessReaders extends LowerPriorityShapelessReaders {
  this: BackendComponent with TypesComponent with SealedTraitDiscriminatorComponent
    with DefaultValuesComponent with ExceptionsComponent =>
  
  implicit def optionFieldTypeReaderTagged[K <: Symbol, V, T](implicit kw: Witness.Aux[K],
                                                              vr: Lazy[Reader[V]],
                                                              dv: DefaultValue.Aux[T, K, Option[V]])
      : Reader[FieldType[K, Option[V]] @@@ T] =
    Reader.reading {
      case backend.Get.Object(v) =>
        val value = backend.getObjectKey(v, kw.value.name).map(vr.value.read).orElse(dv.value.flatten)
        SourceTypeTag[T].attachTo(field[K](value))
    }.orThrowing(whenReading = s"case class field '${kw.value.name}'", expected = "object")

  implicit def recordHeadReader[H, T <: HList](implicit hr: Lazy[Reader[H]], tr: Lazy[Reader[T]],
                                               ev: H <:< FieldType[_, _]): Reader[H :: T] =
    Reader.reading {
      case bv@backend.Get.Object(_) => hr.value.read(bv) :: tr.value.read(bv)
    }.orThrowing(whenReading = "case class", expected = "object")

  implicit val hnilReader: Reader[HNil] = Reader { case _ => HNil }

  protected class ObjectWithDiscriminatorExtractor(discriminator: String) {
    def unapply(value: backend.BValue): Option[String] =
      backend.Extract.Object.unapply(value)
        .flatMap(_.get(discriminator))
        .flatMap(backend.Extract.String.unapply)
  }

  implicit def coproductReader[K <: Symbol, V, U, R <: Coproduct](implicit vr: Lazy[Reader[V]],
                                                                  tr: Lazy[Reader[R]],
                                                                  discriminator: Discriminator.Aux[U] = null,
                                                                  kw: Witness.Aux[K]): Reader[(FieldType[K, V] @@@ U) :+: R] = {
    val dkey = Option(discriminator).flatMap(_.value).getOrElse(defaultDiscriminatorKey)
    val ObjectWithDiscriminator = new ObjectWithDiscriminatorExtractor(dkey)
    Reader.reading[(FieldType[K, V] @@@ U) :+: R] {
      case bv@ObjectWithDiscriminator(key) =>
        if (key == kw.value.name) Inl[FieldType[K, V] @@@ U, R](SourceTypeTag[U].attachTo(field[K](vr.value.read(bv))))
        else Inr[FieldType[K, V] @@@ U, R](tr.value.read(bv))
    }.orThrowing(
        whenReading = "sealed trait hierarchy member",
        expected = s"object with discriminator key '$defaultDiscriminatorKey'"
      )
  }

  implicit val cnilReader: Reader[CNil] = Reader {
    case bv =>
      throw ReadException(
        reading = "sealed trait hierarchy member",
        expected = s"object with discriminator key '$defaultDiscriminatorKey' equal to known value",
        got = bv
      )
  }
}

trait ShapelessReaderWritersComponent extends ShapelessReaders with ShapelessWriters {
  this: BackendComponent with TypesComponent with SealedTraitDiscriminatorComponent
    with DefaultValuesComponent with ExceptionsComponent =>
}
