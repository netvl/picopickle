package io.github.netvl.picopickle

trait Pickler {
  this: BackendComponent with TypesComponent =>
  def read[T: Reader](value: backend.BValue): T
  def write[T: Writer](value: T): backend.BValue
}

trait DefaultPickler
  extends Pickler
  with ShapelessReaderWritersComponent
  with AnnotationSupportingSymbolicLabellingComponent
  with DefaultSealedTraitDiscriminatorComponent
  with PrimitiveReaderWritersComponent
  with CollectionReaderWritersComponent
  with TupleReaderWritersComponent
  with ExtractorsComponent
  with TypesComponent {
  this: BackendComponent =>

  override def read[T](value: backend.BValue)(implicit r: Reader[T]): T = r.read(value)
  override def write[T](value: T)(implicit w: Writer[T]): backend.BValue = w.write(value)
}


