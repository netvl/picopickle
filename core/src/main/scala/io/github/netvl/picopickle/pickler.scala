package io.github.netvl.picopickle

trait Pickler {
  self: BackendComponent with TypesComponent =>
  def read[T: Reader](value: backend.BValue): T
  def write[T: Writer](value: T): backend.BValue

  class Serializer[T: Reader: Writer] {
    def read(value: backend.BValue): T = self.read(value)
    def write(value: T): backend.BValue = self.write(value)
  }
  def serializer[T: Reader: Writer] = new Serializer[T]
}

trait DefaultPickler
  extends Pickler
  with ShapelessReaderWritersComponent
  with DefaultValuesComponent
  with DefaultNullHandlerComponent
  with AnnotationSupportingSymbolicLabellingComponent
  with DefaultSealedTraitDiscriminatorComponent
  with PrimitiveReaderWritersComponent
  with CollectionReaderWritersComponent
  with TupleReaderWritersComponent
  with ConvertersComponent
  with TypesComponent {
  this: BackendComponent =>

  override def read[T](value: backend.BValue)(implicit r: Reader[T]): T = r.read(value)
  override def write[T](value: T)(implicit w: Writer[T]): backend.BValue = w.write(value)
}


