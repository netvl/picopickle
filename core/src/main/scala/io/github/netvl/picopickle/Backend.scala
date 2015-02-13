package io.github.netvl.picopickle

import shapeless.syntax.typeable._

trait BackendComponent {
  val backend: Backend
}

trait Backend {
  type BValue
  type BObject <: BValue
  type BArray <: BValue
  type BString <: BValue
  type BNumber <: BValue
  type BBoolean <: BValue
  type BNull <: BValue

  def fromObject(obj: BObject): Map[String, BValue]
  def makeObject(m: Map[String, BValue]): BObject
  def getObject(value: BValue): Option[BObject]

  def getObjectKey(obj: BObject, key: String): Option[BValue]
  def setObjectKey(obj: BObject, key: String, value: BValue): BObject
  def removeObjectKey(obj: BObject, key: String): BObject
  def makeEmptyObject: BObject = makeObject(Map.empty)

  def fromArray(arr: BArray): Vector[BValue]
  def makeArray(v: Vector[BValue]): BArray
  def getArray(value: BValue): Option[BArray]

  def fromString(str: BString): String
  def makeString(s: String): BString
  def getString(value: BValue): Option[BString]

  def fromNumber(num: BNumber): Number
  def makeNumber(n: Number): BNumber
  def getNumber(value: BValue): Option[BNumber]

  def fromBoolean(bool: BBoolean): Boolean
  def makeBoolean(b: Boolean): BBoolean
  def getBoolean(value: BValue): Option[BBoolean]

  def makeNull: BNull
  def getNull(value: BValue): Option[BNull]

  object Extract {
    object Object {
      def unapply(value: BValue): Option[Map[String, BValue]] = getObject(value).map(fromObject)
    }

    object Array {
      def unapply(value: BValue): Option[Vector[BValue]] = getArray(value).map(fromArray)
    }

    object String {
      def unapply(value: BValue): Option[String] = getString(value).map(fromString)
    }

    object Number {
      def unapply(value: BValue): Option[Number] = getNumber(value).map(fromNumber)
    }

    object Boolean {
      def unapply(value: BValue): Option[Boolean] = getBoolean(value).map(fromBoolean)
    }
  }

  object From {
    object Object {
      def unapply(value: BObject): Option[Map[String, BValue]] = Some(Backend.this.fromObject(value))
    }

    object Array {
      def unapply(value: BArray): Option[Vector[BValue]] = Some(Backend.this.fromArray(value))
    }

    object String {
      def unapply(value: BString): Option[String] = Some(Backend.this.fromString(value))
    }

    object Number {
      def unapply(value: BNumber): Option[Number] = Some(Backend.this.fromNumber(value))
    }

    object Boolean {
      def unapply(value: BBoolean): Option[Boolean] = Some(Backend.this.fromBoolean(value))
    }
  }

  object Get {
    object Object {
      def unapply(value: BValue): Option[BObject] = Backend.this.getObject(value)
    }

    object Array {
      def unapply(value: BValue): Option[BArray] = Backend.this.getArray(value)
    }

    object String {
      def unapply(value: BValue): Option[BString] = Backend.this.getString(value)
    }

    object Number {
      def unapply(value: BValue): Option[BNumber] = Backend.this.getNumber(value)
    }

    object Boolean {
      def unapply(value: BValue): Option[BBoolean] = Backend.this.getBoolean(value)
    }

    object Null {
      def unapply(value: BValue): Option[BNull] = Backend.this.getNull(value)
    }
  }
}


