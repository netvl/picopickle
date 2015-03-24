package io.github.netvl.picopickle

import shapeless.SingletonTypeMacros

import scala.reflect.macros.whitebox

class DefaultValueMacrosImpl(override val c: whitebox.Context) extends SingletonTypeMacros(c) with DefaultValueMacros {
  override def unapplySingletonSymbolType(t: c.Type): Option[String] = SingletonSymbolType.unapply(t)
}
