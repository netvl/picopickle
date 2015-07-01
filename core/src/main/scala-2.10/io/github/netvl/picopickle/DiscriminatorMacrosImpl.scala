package io.github.netvl.picopickle

import scala.reflect.macros.Context

class DiscriminatorMacrosImpl[C <: Context](override val c: C) extends DiscriminatorMacros

object DiscriminatorMacrosImpl {
  def inst(c: Context) = new DiscriminatorMacrosImpl[c.type](c)

  def materializeDiscriminatorImpl[S: c.WeakTypeTag, T: c.WeakTypeTag](c: Context): c.Expr[S] =
    c.Expr[S](inst(c).materializeDiscriminatorImpl[S, T])
}
