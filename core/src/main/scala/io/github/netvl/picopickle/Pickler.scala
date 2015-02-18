package io.github.netvl.picopickle

trait Pickler {
  this: BackendComponent with TypesComponent =>
  def read[T: Reader](value: backend.BValue): T
  def write[T: Writer](value: T): backend.BValue
}

trait DefaultPickler
  extends Pickler
  with ShapelessReaderWritersComponent
  with DefaultSealedTraitDiscriminator
  with PrimitiveReaderWritersComponent
  with CollectionReaderWritersComponent
  with TupleReaderWritersComponent
  with TypesComponent {
  this: BackendComponent =>

  override def read[T: Reader](value: backend.BValue): T =
    implicitly[Reader[T]].read(value)

  override def write[T: Writer](value: T): backend.BValue =
    implicitly[Writer[T]].write(value)
}


