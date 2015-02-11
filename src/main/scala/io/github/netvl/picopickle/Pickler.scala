package io.github.netvl.picopickle

import io.github.netvl.picopickle.backends.json.JsonBackendComponent

trait Pickler {
  this: BackendComponent with TypesComponent =>
  def read[T: Reader](value: backend.BValue): T
  def write[T: Writer](value: T): backend.BValue
}

trait DefaultPickler
  extends Pickler
  with ShapelessReaderWritersComponent
  with PrimitiveReaderWritersComponent
  with CollectionReaderWritersComponent
  with TupleReaderWritersComponent {
  this: BackendComponent with TypesComponent =>

  override def read[T: Reader](value: backend.BValue): T =
    implicitly[Reader[T]].read(value)

  override def write[T: Writer](value: T): backend.BValue =
    implicitly[Writer[T]].write(value)
}

trait JsonPickler extends DefaultPickler with TypesComponent with JsonBackendComponent
object JsonPickler extends JsonPickler