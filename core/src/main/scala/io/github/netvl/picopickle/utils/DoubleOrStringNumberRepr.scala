package io.github.netvl.picopickle.utils

import io.github.netvl.picopickle.Backend

trait DoubleOrStringNumberRepr {
  this: Backend =>

  protected final val MaxLongInDouble: Long = 1L << 53  // Double has 53 bits of precision

  protected def numberToBackendNumberOrString(value: Any): BValue = value match {
    case x @ Double.PositiveInfinity => makeString(x.toString)
    case x @ Double.NegativeInfinity => makeString(x.toString)
    case x: Double if x.isNaN => makeString(x.toString)
    case x @ Float.PositiveInfinity => makeString(x.toString)
    case x @ Float.NegativeInfinity => makeString(x.toString)
    case x: Float if x.isNaN => makeString(x.toString)
    // those longs which do not fit into double
    case x: Long if x.abs > MaxLongInDouble => makeString(x.toString)
    case x: Number => makeNumber(x)
  }

  protected def doubleOrStringFromBackendNumberOrString(value: BValue): Number = value match {
    case Extract.Number(n) => n
    case Extract.String(s)
      if s == Double.PositiveInfinity.toString ||
         s == Double.NegativeInfinity.toString ||
         s == Double.NaN.toString => s.toDouble
    case Extract.String(s) => s.toLong  // handles big longs
  }
}
