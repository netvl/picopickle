package io.github.netvl.picopickle

import scala.language.experimental.macros
import reflect.macros.Context
import shapeless._

import io.github.netvl.picopickle.SourceTypeTag.@@@

trait DefaultValuesComponent {

  // I have *no* idea why this trait and its materialization does not work outside of a cake component
  // without explicit imports o_O
  trait DefaultValue {
    type T
    type K
    type V
    def value: Option[V]
  }

  object DefaultValue {
    type Aux[T0, K0, V0] = DefaultValue { type T = T0; type K = K0; type V = V0 }
    implicit def materializeDefaultValue[T, K <: Symbol, V]: DefaultValue.Aux[T, K, V] =
      macro DefaultValueMacros.materializeDefaultValueImpl[DefaultValue.Aux[T, K, V], T, K, V]
  }
}

object DefaultValueMacros {
  def inst(c: Context) = new DefaultValueMacros[c.type](c)

  def materializeDefaultValueImpl[S: c.WeakTypeTag, T: c.WeakTypeTag, K: c.WeakTypeTag, V: c.WeakTypeTag](c: Context): c.Expr[S] =
    c.Expr[S](inst(c).materializeDefaultValueImpl[T, K, V])
}

class DefaultValueMacros[C <: Context](override val c: C) extends SingletonTypeMacros[C](c) {
  import c.universe._

  def materializeDefaultValueImpl[T: WeakTypeTag, K: WeakTypeTag, V: WeakTypeTag]: Tree = {
    val kTpe = weakTypeOf[K].normalize
    val fieldName = kTpe match {
      case SingletonSymbolType(s) => s
      case _ => c.abort(c.enclosingPosition, s"Type $kTpe is not a tagged symbol type")
    }

    val tTpe = weakTypeOf[T]
    val tCompanionSym = tTpe.typeSymbol.companionSymbol

    if (tCompanionSym == NoSymbol)
      c.abort(c.enclosingPosition, s"No companion symbol is available for type $tTpe")

    val applySym = tCompanionSym.typeSignature.declaration(newTermName("apply")).asMethod
    if (applySym == NoSymbol)
      c.abort(c.enclosingPosition, s"Companion symbol for type $tTpe does not have apply method")

    val vTpe = weakTypeOf[V]

    val defaultMethodName = applySym.paramss.headOption.flatMap { argSyms =>
      argSyms.map(_.asTerm).zipWithIndex.collect {
        case (p, i) if p.isParamWithDefault && p.name.toString == fieldName && p.typeSignature =:= vTpe =>
          newTermName(s"apply$$default$$${i+1}")
      }.headOption
    }

    val invocation = defaultMethodName match {
      case Some(name) => q"_root_.scala.Some($tCompanionSym.$name)"
      case None => q"_root_.scala.None"
    }

    val generatedClassName = newTypeName(s"DefaultValue$$${tTpe.typeSymbol.name}$$$fieldName")
    q"""
      {
        final class $generatedClassName extends DefaultValue {
          type T = $tTpe
          type K = $kTpe
          type V = $vTpe
          def value: _root_.scala.Option[$vTpe] = $invocation
        }
        new $generatedClassName
      }
    """
  }
}

object SourceTypeTag {
  def apply[U] = new Tagger[U]

  trait Tag[T]
  type @@@[+T, U] = T with Tag[U]

  class Tagger[U] {
    def attachTo[T](value: T): T @@@ U = value.asInstanceOf[T @@@ U]
  }
}

trait TagWithType[L, U] {
  type Out
  def unwrap(source: Out): L
}
object TagWithType {
  def apply[L, U](implicit t: TagWithType[L, U]): Aux[L, U, t.Out] = t

  type Aux[L, U, Out0] = TagWithType[L, U] { type Out = Out0 }

  implicit def tagWithTypeHNil[U]: TagWithType.Aux[HNil, U, HNil] = new TagWithType[HNil, U] {
    type Out = HNil
    def unwrap(source: HNil): HNil = HNil
  }

  implicit def tagWithTypeHCons[U, H, T <: HList, O <: HList](implicit tt: TagWithType.Aux[T, U, O])
      : TagWithType.Aux[H :: T, U, (H @@@ U) :: O] =
    new TagWithType[H :: T, U] {
      type Out = (H @@@ U) :: O
      def unwrap(source: (H @@@ U) :: O): H :: T = source match {
        case h :: t => h :: tt.unwrap(t)
      }
    }
}
