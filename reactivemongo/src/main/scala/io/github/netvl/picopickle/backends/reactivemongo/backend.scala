package io.github.netvl.picopickle.backends.reactivemongo

import io.github.netvl.picopickle.Backend
import reactivemongo.bson._

object ReactivemongoBsonBackend
/* extends Backend {
  override type BValue = BSONValue
  override type BObject = BSONDocument
  override type BArray = BSONArray
  override type BString = BSONString
  override type BNumber = BSONNumberLike
  override type BBoolean = BSONBoolean
  override type BNull = BSONNull.type

  type BObjectId = BSONObjectID
  type BInt32 = BSONInteger
  type BInt64 = BSONLong
  type BDouble = BSONDouble
  type BDateTime = BSONDateTime
  type BBinary = BSONBinary
  type BSymbol = BSONSymbol

  override def pushToArray(arr: ReactivemongoBsonBackend.type, value: ReactivemongoBsonBackend.BValue): ReactivemongoBsonBackend.type = ???

  override def fromNumberAccurately: PartialFunction[ReactivemongoBsonBackend.BValue, Number] = ???

  override def makeNumber(n: Number): ReactivemongoBsonBackend.type = ???

  override def getArray(value: ReactivemongoBsonBackend.BValue): Option[ReactivemongoBsonBackend.type] = ???

  override def makeString(s: String): ReactivemongoBsonBackend.type = ???

  override def getArrayLength(arr: ReactivemongoBsonBackend.type): Int = ???

  override def makeNull: ReactivemongoBsonBackend.type = ???

  override def getObjectKey(obj: ReactivemongoBsonBackend.type, key: String): Option[ReactivemongoBsonBackend.BValue] = ???

  override def fromBoolean(bool: ReactivemongoBsonBackend.type): Boolean = ???

  override def getNull(value: ReactivemongoBsonBackend.BValue): Option[ReactivemongoBsonBackend.type] = ???

  override def removeObjectKey(obj: ReactivemongoBsonBackend.type, key: String): ReactivemongoBsonBackend.type = ???

  override def getArrayValueAt(arr: ReactivemongoBsonBackend.type, idx: Int): ReactivemongoBsonBackend.BValue = ???

  override def fromObject(obj: ReactivemongoBsonBackend.type): Map[String, ReactivemongoBsonBackend.BValue] = ???

  override def getBoolean(value: ReactivemongoBsonBackend.BValue): Option[ReactivemongoBsonBackend.type] = ???

  override def getObject(value: ReactivemongoBsonBackend.BValue): Option[ReactivemongoBsonBackend.type] = ???

  override def makeBoolean(b: Boolean): ReactivemongoBsonBackend.type = ???

  override def fromNumber(num: ReactivemongoBsonBackend.type): Number = ???

  override def fromArray(arr: ReactivemongoBsonBackend.type): Vector[ReactivemongoBsonBackend.BValue] = ???

  override def makeNumberAccurately(n: Number): ReactivemongoBsonBackend.BValue = ???

  override def setObjectKey(obj: ReactivemongoBsonBackend.type, key: String, value: ReactivemongoBsonBackend.BValue): ReactivemongoBsonBackend.type = ???

  override def fromString(str: ReactivemongoBsonBackend.type): String = ???

  override def makeObject(m: Map[String, ReactivemongoBsonBackend.BValue]): ReactivemongoBsonBackend.type = ???

  override def getNumber(value: ReactivemongoBsonBackend.BValue): Option[ReactivemongoBsonBackend.type] = ???

  override def makeArray(v: Vector[ReactivemongoBsonBackend.BValue]): ReactivemongoBsonBackend.type = ???

  override def getString(value: ReactivemongoBsonBackend.BValue): Option[ReactivemongoBsonBackend.type] = ???

  override def fromNumberAccuratelyExpected: String = ???
}*/