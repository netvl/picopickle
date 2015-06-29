package io.github.netvl.picopickle

trait NullHandlerComponent {
  this: TypesComponent with BackendComponent =>

  def nullHandler: NullHandler

  trait NullHandler {
    def handlesNull: Boolean
    def toBackend[T](value: T, cont: T => backend.BValue): backend.BValue
    def fromBackend[T](value: backend.BValue, cont: backend.BValue => T): T
  }
}

trait DefaultNullHandlerComponent extends NullHandlerComponent {
  this: TypesComponent with BackendComponent =>

  override def nullHandler: NullHandler = new NullHandler {
    override def handlesNull: Boolean = true

    override def fromBackend[T](value: backend.BValue, cont: backend.BValue => T): T = value match {
      case backend.Get.Null(_) => null.asInstanceOf[T]
      case _ => cont(value)
    }

    override def toBackend[T](value: T, cont: T => backend.BValue): backend.BValue = value match {
      case null => backend.makeNull
      case _ => cont(value)
    }
  }
}

trait ProhibitiveNullHandlerComponent extends NullHandlerComponent {
  this: TypesComponent with BackendComponent with ExceptionsComponent =>

  override def nullHandler: NullHandler = new NullHandler {
    override def handlesNull: Boolean = false

    override def fromBackend[T](value: backend.BValue, cont: backend.BValue => T): T = value match {
      case backend.Get.Null(_) => throw ReadException("null values are prohibited")
      case _ => cont(value)
    }

    override def toBackend[T](value: T, cont: T => backend.BValue): backend.BValue = value match {
      case null => throw ReadException("null values are prohibited")
      case _ => cont(value)
    }
  }
}
