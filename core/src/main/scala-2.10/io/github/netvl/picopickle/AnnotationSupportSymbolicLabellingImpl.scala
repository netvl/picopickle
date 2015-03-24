package io.github.netvl.picopickle

import shapeless.{DefaultSymbolicLabelling, LabelledMacros}

import scala.reflect.macros.Context

class AnnotationSupportSymbolicLabellingImpl[C <: Context](override val c: C) extends LabelledMacros[C](c) with AnnotationSupportSymbolicLabelling {
  override def mkSingletonSymbolType(s: String): c.Type = SingletonSymbolType(s)

  override def mkDefaultSymbolicLabellingImpl[T](implicit tTag: c.WeakTypeTag[T]): c.Tree =
    AnnotationSupportSymbolicLabellingImpl.super.mkDefaultSymbolicLabellingImpl[T](tTag)
}

object AnnotationSupportSymbolicLabellingImpl {
  def inst(c: Context) = new AnnotationSupportSymbolicLabellingImpl[c.type](c)

  def mkDefaultSymbolicLabellingImpl[T: c.WeakTypeTag](c: Context): c.Expr[DefaultSymbolicLabelling[T]] =
    c.Expr[DefaultSymbolicLabelling[T]](inst(c).mkDefaultSymbolicLabellingImpl[T])
}
