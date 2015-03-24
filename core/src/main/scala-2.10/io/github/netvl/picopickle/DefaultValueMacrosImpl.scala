package io.github.netvl.picopickle

import shapeless.SingletonTypeMacros

import scala.reflect.macros.Context

class DefaultValueMacrosImpl[C <: Context](override val c: C) extends SingletonTypeMacros[C](c) with DefaultValueMacros {
  override def unapplySingletonSymbolType(t: c.Type): Option[String] = SingletonSymbolType.unapply(t)
}

object DefaultValueMacrosImpl {
  def inst(c: Context) = new DefaultValueMacrosImpl[c.type](c)

  def materializeDefaultValueImpl[S: c.WeakTypeTag, T: c.WeakTypeTag, K: c.WeakTypeTag, V: c.WeakTypeTag](c: Context): c.Expr[S] =
    c.Expr[S](inst(c).materializeDefaultValueImpl[S, T, K, V])
}
