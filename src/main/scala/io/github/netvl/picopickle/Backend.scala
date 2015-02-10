package io.github.netvl.picopickle

import shapeless.syntax.typeable._

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

  object Extractors {
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

object JsonAst {
  sealed trait JsonValue

  case class JsonObject(values: Map[String, JsonValue]) extends JsonValue
  case class JsonArray(values: Vector[JsonValue]) extends JsonValue
  case class JsonString(value: String) extends JsonValue
  case class JsonNumber(value: Double) extends JsonValue
  
  sealed trait JsonBoolean extends JsonValue
  case object JsonTrue extends JsonBoolean
  case object JsonFalse extends JsonBoolean
  
  case object JsonNull extends JsonValue
  
  object Backend extends Backend {
    override type BValue = JsonValue
    override type BObject = JsonObject
    override type BArray = JsonArray
    override type BString = JsonString
    override type BNumber = JsonNumber
    override type BBoolean = JsonBoolean
    override type BNull = JsonNull.type

    override def fromObject(obj: BObject): Map[String, BValue] = obj.values
    override def makeObject(m: Map[String, BValue]): BObject = JsonObject(m)
    override def getObject(value: BValue): Option[BObject] = value.cast[JsonObject]

    override def getObjectKey(obj: BObject, key: String): Option[BValue] =
      obj.values.get(key)
    override def setObjectKey(obj: BObject, key: String, value: BValue): BObject =
      obj.copy(values = obj.values + (key -> value))
    override def removeObjectKey(obj: BObject, key: String): BObject =
      obj.copy(values = obj.values - key)

    override def fromArray(arr: BArray): Vector[BValue] = arr.values
    override def makeArray(v: Vector[BValue]): BArray = JsonArray(v)
    override def getArray(value: BValue): Option[BArray] = value.cast[JsonArray]

    override def fromString(str: BString): String = str.value
    override def makeString(s: String): BString = JsonString(s)
    override def getString(value: BValue): Option[BString] = value.cast[JsonString]

    override def fromNumber(num: BNumber): Number = num.value
    override def makeNumber(n: Number): BNumber = JsonNumber(n.doubleValue())
    override def getNumber(value: BValue): Option[BNumber] = value.cast[JsonNumber]

    override def fromBoolean(bool: BBoolean): Boolean = bool match {
      case JsonTrue => true
      case JsonFalse => false
    }
    override def makeBoolean(b: Boolean): BBoolean = if (b) JsonTrue else JsonFalse
    override def getBoolean(value: BValue): Option[BBoolean] = value.cast[JsonBoolean]

    override def makeNull: BNull = JsonNull
    override def getNull(value: BValue): Option[BNull] = value.cast[JsonNull.type]
  }
}
