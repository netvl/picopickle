package io.github.netvl.picopickle

import shapeless.LabelledMacros

import scala.reflect.macros.whitebox

class AnnotationSupportSymbolicLabellingImpl(override val c: whitebox.Context) extends LabelledMacros(c) with AnnotationSupportSymbolicLabelling {
  override def mkSingletonSymbolType(s: String): c.Type = SingletonSymbolType(s)

  override def mkDefaultSymbolicLabellingImpl[T](implicit tTag: c.WeakTypeTag[T]): c.Tree =
    AnnotationSupportSymbolicLabellingImpl.super.mkDefaultSymbolicLabellingImpl[T](tTag)
}
