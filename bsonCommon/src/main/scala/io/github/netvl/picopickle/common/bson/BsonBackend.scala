package io.github.netvl.picopickle.common.bson

import io.github.netvl.picopickle.Backend
import java.lang.{Byte => JByte, Short => JShort, Integer => JInt, Long => JLong, Float => JFloat, Double => JDouble}

sealed trait BsonBinaryType {
  def value: Byte
}
object BsonBinaryType {
  val Values: Map[BsonBinaryType, Byte] = Iterator(
    Generic, Function, Binary, UuidOld, Uuid, Md5, UserDefined
  ).map(v => v -> v.value).toMap

  def fromByte(value: Byte): BsonBinaryType = Values.find(_._2 == value).map(_._1).getOrElse(Other(value))

  case object Generic extends BsonBinaryType     { def value = 0x00 }
  case object Function extends BsonBinaryType    { def value = 0x01 }
  case object Binary extends BsonBinaryType      { def value = 0x02 }
  case object UuidOld extends BsonBinaryType     { def value = 0x03 }
  case object Uuid extends BsonBinaryType        { def value = 0x04 }
  case object Md5 extends BsonBinaryType         { def value = 0x05 }
  case object UserDefined extends BsonBinaryType { def value = 0x80.toByte }
  case class Other(value: Byte) extends BsonBinaryType
}

trait BsonBackend extends Backend {
  type ObjectId

  type BObjectId <: BValue
  type BInt32 <: BValue
  type BInt64 <: BValue
  type BDouble <: BValue
  type BDateTime <: BValue
  type BBinary <: BValue
  type BSymbol <: BValue
  type BRegex <: BValue
  type BJavaScript <: BValue
  type BJavaScriptWithScope <: BValue
  type BTimestamp <: BValue

  def fromObjectId(oid: BObjectId): ObjectId
  def makeObjectId(oid: ObjectId): BObjectId
  def getObjectId(value: BValue): Option[BObjectId]

  def fromInt32(n: BInt32): Int
  def makeInt32(n: Int): BInt32
  def getInt32(value: BValue): Option[BInt32]

  def fromInt64(n: BInt64): Long
  def makeInt64(n: Long): BInt64
  def getInt64(value: BValue): Option[BInt64]

  def fromDouble(n: BDouble): Double
  def makeDouble(n: Double): BDouble
  def getDouble(value: BValue): Option[BDouble]

  def fromDateTime(dt: BDateTime): Long
  def makeDateTime(n: Long): BDateTime
  def getDateTime(value: BValue): Option[BDateTime]

  def fromBinary(bin: BBinary): (Array[Byte], BsonBinaryType)
  def makeBinary(arr: Array[Byte], subtype: BsonBinaryType = BsonBinaryType.Generic): BBinary
  def getBinary(value: BValue): Option[BBinary]

  def fromSymbol(sym: BSymbol): Symbol
  def makeSymbol(sym: Symbol): BSymbol
  def getSymbol(value: BValue): Option[BSymbol]

  def fromRegex(regex: BRegex): (String, String)
  def makeRegex(pattern: String, options: String): BRegex
  def getRegex(value: BValue): Option[BRegex]

  def fromJavaScript(js: BJavaScript): String
  def makeJavaScript(code: String): BJavaScript
  def getJavaScript(value: BValue): Option[BJavaScript]

  def fromJavaScriptWithScope(js: BJavaScriptWithScope): (String, BObject)
  def makeJavaScriptWithScope(code: String, scope: BObject): BJavaScriptWithScope
  def getJavaScriptWithScope(value: BValue): Option[BJavaScriptWithScope]

  def fromTimestamp(ts: BTimestamp): Long
  def makeTimestamp(n: Long): BTimestamp
  def getTimestamp(value: BValue): Option[BTimestamp]

  override def makeNumberAccurately(n: Number): BValue = n match {
    case _: JByte | _: JShort | _: JInt => makeInt32(n.intValue())
    case _: JLong => makeInt64(n.longValue())
    case _: JFloat | _: JDouble => makeDouble(n.doubleValue())
    case _ => makeDouble(n.doubleValue())  // FIXME: there are other types which should probably be handled more properly
  }
  override def fromNumberAccurately: PartialFunction[BValue, Number] = {
    case BsonExtract.Int32(n) => n
    case BsonExtract.Int64(n) => n
    case BsonExtract.Double(n) => n
  }
  override def fromNumberAccuratelyExpected: String = "number"

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
      def unapply(value: BValue): Option[(Array[Byte], BsonBinaryType)] = getBinary(value).map(fromBinary)
    }

    object Symbol {
      def unapply(value: BValue): Option[Symbol] = getSymbol(value).map(fromSymbol)
    }

    object Regex {
      def unapply(value: BValue): Option[(String, String)] = getRegex(value).map(fromRegex)
    }

    object JavaScript {
      def unapply(value: BValue): Option[String] = getJavaScript(value).map(fromJavaScript)
    }

    object JavaScriptWithScope {
      def unapply(value: BValue): Option[(String, BObject)] = getJavaScriptWithScope(value).map(fromJavaScriptWithScope)
    }

    object Timestamp {
      def unapply(value: BValue): Option[Long] = getTimestamp(value).map(fromTimestamp)
    }
  }

  object bsonConversionImplicits {
    implicit class ObjectIdToBackendExt(val oid: ObjectId) {
      def toBackend: BObjectId = makeObjectId(oid)
    }

    implicit class BinaryToBackendExt(val arr: Array[Byte]) {
      def toBackend: BBinary = makeBinary(arr)
    }

    implicit class TypedBinaryToBackendExt(val arr: (Array[Byte], BsonBinaryType)) {
      def toBackend: BBinary = makeBinary(arr._1, arr._2)
    }

    implicit class SymbolToBackendExt(val sym: Symbol) {
      def toBackend: BSymbol = makeSymbol(sym)
    }
  }

}
