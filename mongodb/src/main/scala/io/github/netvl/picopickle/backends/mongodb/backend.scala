package io.github.netvl.picopickle.backends.mongodb

import io.github.netvl.picopickle.Backend
import org.bson._
import org.bson.types.ObjectId
import scala.collection.convert.decorateAll._
import shapeless.syntax.typeable._

object MongodbBsonBackend extends Backend {
  override type BValue = BsonValue
  override type BObject = BsonDocument
  override type BArray = BsonArray
  override type BString = BsonString
  override type BNumber = BsonNumber
  override type BBoolean = BsonBoolean
  override type BNull = BsonNull

  type BObjectId = BsonObjectId
  type BInt32 = BsonInt32
  type BInt64 = BsonInt64
  type BDouble = BsonDouble
  type BDateTime = BsonDateTime
  type BBinary = BsonBinary
  type BSymbol = BsonSymbol

  // XXX: do we need to make copies instead of mutating the original values here?

  override def fromObject(obj: BObject): Map[String, BValue] = obj.asScala.toMap
  override def makeObject(m: Map[String, BValue]): BObject = m.foldLeft(new BsonDocument()) { case (d, (k, v)) => d.append(k, v) }
  override def getObject(value: BValue): Option[BObject] = value.cast[BsonDocument]

  override def getObjectKey(obj: BObject, key: String): Option[BValue] = Option(obj.get(key))  // values can't be null
  override def setObjectKey(obj: BObject, key: String, value: BValue): BObject = obj.append(key, value)
  override def containsObjectKey(obj: BObject, key: String): Boolean = obj.containsKey(key)
  override def removeObjectKey(obj: BObject, key: String): BObject = { obj.remove(key); obj }
  override def makeEmptyObject: BObject = new BsonDocument()

  override def fromArray(arr: BArray): Vector[BValue] = arr.asScala.toVector
  override def makeArray(v: Vector[BValue]): BArray = new BsonArray(v.asJava)
  override def getArray(value: BValue): Option[BArray] = value.cast[BsonArray]

  override def getArrayLength(arr: BArray): Int = arr.size()
  override def getArrayValueAt(arr: BArray, idx: Int): BValue = arr.get(idx)
  override def pushToArray(arr: BArray, value: BValue): BArray = { arr.add(value); arr }
  override def makeEmptyArray: BArray = new BsonArray()
  
  def fromBinary(bin: BBinary): Array[Byte] = bin.getData
  def makeBinary(arr: Array[Byte]): BBinary = new BsonBinary(arr)
  def getBinary(value: BValue): Option[BBinary] = value.cast[BBinary]
  
  def fromObjectId(oid: BObjectId): ObjectId = oid.getValue
  def makeObjectId(oid: ObjectId): BObjectId = new BsonObjectId(oid)
  def getObjectId(value: BValue): Option[BObjectId] = value.cast[BsonObjectId]
  
  def fromDateTime(dt: BDateTime): Long = dt.getValue
  def makeDateTime(n: Long): BDateTime = new BsonDateTime(n)
  def getDateTime(value: BValue): Option[BDateTime] = value.cast[BsonDateTime]

  override def fromString(str: BString): String = str.getValue
  override def makeString(s: String): BString = new BsonString(s)
  override def getString(value: BValue): Option[BString] = value.cast[BsonString]

  def fromSymbol(sym: BSymbol): Symbol = Symbol(sym.getSymbol)
  def makeSymbol(sym: Symbol): BSymbol = new BsonSymbol(sym.name)
  def getSymbol(value: BValue): Option[BSymbol] = value.cast[BsonSymbol]

  def fromInt32(n: BInt32): Int = n.getValue
  def makeInt32(n: Int): BInt32 = new BsonInt32(n)
  def getInt32(value: BValue): Option[BsonInt32] = value.cast[BsonInt32]

  def fromInt64(n: BInt64): Long = n.getValue
  def makeInt64(n: Long): BInt64 = new BsonInt64(n)
  def getInt64(value: BValue): Option[BsonInt64] = value.cast[BsonInt64]

  def fromDouble(n: BDouble): Double = n.getValue
  def makeDouble(n: Double): BDouble = new BsonDouble(n)
  def getDouble(value: BValue): Option[BsonDouble] = value.cast[BsonDouble]

  override def fromNumber(num: BNumber): Number = fromNumberAccurately(num)
  override def makeNumber(n: Number): BNumber = makeNumberAccurately(n).asInstanceOf[BNumber]
  override def getNumber(value: BValue): Option[BNumber] = value.cast[BsonNumber]

  override def makeNumberAccurately(n: Number): BValue = n match {
    case (_: java.lang.Byte | _: java.lang.Short | _: java.lang.Integer) => new BsonInt32(n.intValue())
    case _: java.lang.Long => new BsonInt64(n.longValue())
    case _: java.lang.Float | _: java.lang.Double => new BsonDouble(n.doubleValue())
    case _ => new BsonDouble(n.doubleValue())  // FIXME: there are other types which should be handled properly
  }
  override def fromNumberAccurately: PartialFunction[BValue, Number] = {
    case n: BsonInt32 => n.intValue()
    case n: BsonInt64 => n.longValue()
    case n: BsonDouble => n.doubleValue()
  }
  override def fromNumberAccuratelyExpected: String = "number"

  def fromBoolean(bool: BBoolean): Boolean = bool.getValue
  def makeBoolean(b: Boolean): BBoolean = new BsonBoolean(b)
  def getBoolean(value: BValue): Option[BBoolean] = value.cast[BsonBoolean]

  def makeNull: BNull = new BsonNull
  def getNull(value: BValue): Option[BNull] = value.cast[BsonNull]

  object BsonExtract {
    object ObjectId {
      def unapply(value: BValue): Option[ObjectId] = getObjectId(value).map(fromObjectId)
    }

    object Int32 {
      def unapply(value: BValue): Option[Int] = getInt32(value).map(fromInt32)
    }

    object Int64 {
      def unapply(value: BValue): Option[Long] = getInt64(value).map(fromInt64)
    }

    object Double {
      def unapply(value: BValue): Option[Double] = getDouble(value).map(fromDouble)
    }

    object DateTime {
      def unapply(value: BValue): Option[Long] = getDateTime(value).map(fromDateTime)
    }

    object Binary {
      def unapply(value: BValue): Option[Array[Byte]] = getBinary(value).map(fromBinary)
    }

    object Symbol {
      def unapply(value: BValue): Option[Symbol] = getSymbol(value).map(fromSymbol)
    }
  }

  object bsonConversionImplicits {
    implicit class ObjectIdToBackendExt(val oid: ObjectId) {
      def toBackend: BObjectId = makeObjectId(oid)
    }

    implicit class BinaryToBackendExt(val arr: Array[Byte]) {
      def toBackend: BBinary = makeBinary(arr)
    }

    implicit class SymbolToBackendExt(val sym: Symbol) {
      def toBackend: BSymbol = makeSymbol(sym)
    }
  }
}
