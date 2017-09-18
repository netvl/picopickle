package io.github.netvl.picopickle.backends.jawn

import jawn.{FContext, Facade}
import shapeless.syntax.typeable._

import io.github.netvl.picopickle.Backend
import io.github.netvl.picopickle.utils.DoubleOrStringNumberRepr

import scala.annotation.switch
import scala.collection.mutable

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

  object Backend extends Backend with DoubleOrStringNumberRepr {
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
    override def containsObjectKey(obj: BObject, key: String): Boolean =
      obj.values.contains(key)
    override def removeObjectKey(obj: BObject, key: String): BObject =
      obj.copy(values = obj.values - key)

    override def fromArray(arr: BArray): Vector[BValue] = arr.values
    override def makeArray(v: Vector[BValue]): BArray = JsonArray(v)
    override def getArray(value: BValue): Option[BArray] = value.cast[JsonArray]
    override def pushToArray(arr: BArray, value: BValue) = JsonArray(arr.values :+ value)

    override def getArrayLength(arr: BArray): Int = arr.values.length
    override def getArrayValueAt(arr: BArray, idx: Int): BValue = arr.values(idx)

    override def fromString(str: BString): String = str.value
    override def makeString(s: String): BString = JsonString(s)
    override def getString(value: BValue): Option[BString] = value.cast[JsonString]

    override def fromNumber(num: BNumber): Number = num.value
    override def makeNumber(n: Number): BNumber = JsonNumber(n.doubleValue())
    override def getNumber(value: BValue): Option[BNumber] = value.cast[JsonNumber]

    override def makeNumberAccurately(n: Number): BValue = numberToBackendNumberOrString(n)
    override def fromNumberAccurately: PartialFunction[BValue, Number] = doubleOrStringFromBackendNumberOrString
    override def fromNumberAccuratelyExpected: String = backendNumberOrStringExpected

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

object JawnFacade extends MutableFacade[JsonAst.JsonValue] {
  import JsonAst._

  override def jobject(vs: mutable.Builder[(String, JsonValue), Map[String, JsonValue]]): JsonValue =
    JsonObject(vs.result())
  override def jarray(vs: mutable.Builder[JsonValue, Vector[JsonValue]]): JsonValue =
    JsonArray(vs.result())

  override def jnum(s: CharSequence, decIndex: Int, expIndex: Int): JsonValue = JsonNumber(s.toString.toDouble)

  override def jstring(s: CharSequence): JsonValue = JsonString(s.toString)

  override def jtrue(): JsonValue = JsonTrue
  override def jfalse(): JsonValue = JsonFalse

  override def jnull(): JsonValue = JsonNull
}

private[jawn] trait MutableFacade[J] extends Facade[J] {
  def jobject(vs: mutable.Builder[(String, J), Map[String, J]]): J
  def jarray(vs: mutable.Builder[J, Vector[J]]): J

  override def singleContext(): FContext[J] = new FContext[J] {
    private var value: J = _
    override def isObj: Boolean = false
    override def add(s: CharSequence): Unit = value = jstring(s)
    override def add(v: J): Unit = value = v
    override def finish: J = value
  }

  override def objectContext(): FContext[J] = new FContext[J] {
    private var key: String = null
    private val builder = Map.newBuilder[String, J]
    override def isObj: Boolean = true
    override def add(s: CharSequence): Unit =
      if (key == null) key = s.toString
      else {
        builder += key -> jstring(s)
        key = null
      }
    override def add(v: J): Unit = {
      builder += key -> v
      key = null
    }
    override def finish: J = jobject(builder)
  }

  override def arrayContext(): FContext[J] = new FContext[J] {
    private val builder = Vector.newBuilder[J]
    override def isObj: Boolean = false
    override def add(s: CharSequence): Unit = builder += jstring(s)
    override def add(v: J): Unit = builder += v
    override def finish: J = jarray(builder)
  }
}

// Heavily based on upickle's (https://github.com/lihaoyi/upickle) JSON renderer
object JsonRenderer {
  import JsonAst._

  def render(value: JsonValue): String = {
    val sb = new StringBuilder
    render(sb, value)
    sb.toString()
  }

  private def render(sb: StringBuilder, value: JsonValue): Unit = {
    value match {
      case JsonNull => sb.append("null")
      case JsonTrue => sb.append("true")
      case JsonFalse => sb.append("false")
      case JsonNumber(n) => sb.append(if (n.isWhole()) n.toLong.toString else n.toString)
      case JsonString(s) => renderString(sb, s)
      case JsonArray(arr) => renderArray(sb, arr)
      case JsonObject(obj) => renderObject(sb, obj)
    }
  }

  private def renderArray(sb: StringBuilder, arr: Vector[JsonValue]): Unit = {
    if (arr.isEmpty) sb.append("[]")
    else {
      val it = arr.iterator
      sb.append("[")
      render(sb, it.next())
      while (it.hasNext) {
        sb.append(",")
        render(sb, it.next())
      }
      sb.append("]")
    }
  }

  private def renderObject(sb: StringBuilder, obj: Map[String, JsonValue]): Unit = {
    if (obj.isEmpty) sb.append("{}")
    else {
      val it = obj.iterator
      sb.append("{")
      val (k0, v0) = it.next()
      renderString(sb, k0)
      sb.append(":")
      render(sb, v0)
      while (it.hasNext) {
        val (k, v) = it.next()
        sb.append(",")
        renderString(sb, k)
        sb.append(":")
        render(sb, v)
      }
      sb.append("}")
    }
  }

  private def renderString(sb: StringBuilder, s: String): Unit = {
    sb.append('"')
    var i = 0
    val len = s.length
    while (i < len) {
      (s.charAt(i): @switch) match {
        case '"' => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\b' => sb.append("\\b")
        case '\f' => sb.append("\\f")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case c =>
          if (c < ' ') sb.append(f"\\u${c.toInt}%04x")
          else sb.append(c)
      }
      i += 1
    }
    sb.append('"')
  }
}
