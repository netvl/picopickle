package io.github.netvl.picopickle.backends.mongodb

import _root_.io.github.netvl.picopickle.common.bson.{BsonBinaryType, BsonBackend}
import org.bson._
import scala.collection.convert.decorateAll._
import shapeless.syntax.typeable._

object MongodbBsonBackend extends BsonBackend {
  override type ObjectId = types.ObjectId

  override type BValue = BsonValue
  override type BObject = BsonDocument
  override type BArray = BsonArray
  override type BString = BsonString
  override type BNumber = BsonNumber
  override type BBoolean = BsonBoolean
  override type BNull = BsonNull
  override type BObjectId = BsonObjectId
  override type BInt32 = BsonInt32
  override type BInt64 = BsonInt64
  override type BDouble = BsonDouble
  override type BDateTime = BsonDateTime
  override type BBinary = BsonBinary
  override type BSymbol = BsonSymbol
  override type BRegex = BsonRegularExpression
  override type BJavaScript = BsonJavaScript
  override type BJavaScriptWithScope = BsonJavaScriptWithScope
  override type BTimestamp = BsonTimestamp

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

  override def fromString(str: BString): String = str.getValue
  override def makeString(s: String): BString = new BsonString(s)
  override def getString(value: BValue): Option[BString] = value.cast[BsonString]

  override def fromNumber(num: BNumber): Number = fromNumberAccurately(num)
  override def makeNumber(n: Number): BNumber = makeNumberAccurately(n).asInstanceOf[BNumber]
  override def getNumber(value: BValue): Option[BNumber] = value.cast[BsonNumber]

  override def fromBoolean(bool: BBoolean): Boolean = bool.getValue
  override def makeBoolean(b: Boolean): BBoolean = new BsonBoolean(b)
  override def getBoolean(value: BValue): Option[BBoolean] = value.cast[BsonBoolean]

  override def makeNull: BNull = new BsonNull
  override def getNull(value: BValue): Option[BNull] = value.cast[BsonNull]

  override def fromObjectId(oid: BObjectId): ObjectId = oid.getValue
  override def makeObjectId(oid: ObjectId): BObjectId = new BsonObjectId(oid)
  override def getObjectId(value: BValue): Option[BObjectId] = value.cast[BsonObjectId]

  override def fromInt32(n: BInt32): Int = n.getValue
  override def makeInt32(n: Int): BInt32 = new BsonInt32(n)
  override def getInt32(value: BValue): Option[BsonInt32] = value.cast[BsonInt32]

  override def fromInt64(n: BInt64): Long = n.getValue
  override def makeInt64(n: Long): BInt64 = new BsonInt64(n)
  override def getInt64(value: BValue): Option[BsonInt64] = value.cast[BsonInt64]

  override def fromDouble(n: BDouble): Double = n.getValue
  override def makeDouble(n: Double): BDouble = new BsonDouble(n)
  override def getDouble(value: BValue): Option[BsonDouble] = value.cast[BsonDouble]

  override def fromDateTime(dt: BDateTime): Long = dt.getValue
  override def makeDateTime(n: Long): BDateTime = new BsonDateTime(n)
  override def getDateTime(value: BValue): Option[BDateTime] = value.cast[BsonDateTime]

  override def fromBinary(bin: BBinary): (Array[Byte], BsonBinaryType) = (bin.getData, BsonBinaryType.fromByte(bin.getType))
  override def makeBinary(arr: Array[Byte], subtype: BsonBinaryType): BBinary = new BsonBinary(subtype.value, arr)
  override def getBinary(value: BValue): Option[BBinary] = value.cast[BBinary]

  override def fromSymbol(sym: BSymbol): Symbol = Symbol(sym.getSymbol)
  override def makeSymbol(sym: Symbol): BSymbol = new BsonSymbol(sym.name)
  override def getSymbol(value: BValue): Option[BSymbol] = value.cast[BsonSymbol]

  override def fromRegex(regex: BRegex): (String, String) = (regex.getPattern, regex.getOptions)
  override def makeRegex(pattern: String, options: String): BRegex = new BsonRegularExpression(pattern, options)
  override def getRegex(value: BsonValue): Option[BRegex] = value.cast[BsonRegularExpression]

  override def fromTimestamp(ts: BTimestamp): Long = (ts.getTime << 32) | ts.getInc.toLong
  override def makeTimestamp(n: Long): BTimestamp = new BsonTimestamp((n >>> 32).toInt, n.toInt)
  override def getTimestamp(value: BsonValue): Option[BTimestamp] = value.cast[BTimestamp]

  override def fromJavaScript(js: BsonJavaScript): String = js.getCode
  override def makeJavaScript(code: String): BsonJavaScript = new BsonJavaScript(code)
  override def getJavaScript(value: BsonValue): Option[BsonJavaScript] = value.cast[BsonJavaScript]

  override def fromJavaScriptWithScope(js: BsonJavaScriptWithScope): (String, BObject) = (js.getCode, js.getScope)
  override def makeJavaScriptWithScope(code: String, scope: BsonDocument): BJavaScriptWithScope = new BsonJavaScriptWithScope(code, scope)
  override def getJavaScriptWithScope(value: BsonValue): Option[BJavaScriptWithScope] = value.cast[BJavaScriptWithScope]
}
