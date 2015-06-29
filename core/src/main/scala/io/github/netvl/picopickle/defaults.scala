package io.github.netvl.picopickle

import scala.language.experimental.macros
import shapeless._

import io.github.netvl.picopickle.SourceTypeTag.@@@
import BinaryVersionSpecificDefinitions._

trait DefaultValuesComponent {

  // I have *no* idea why this trait and its materialization do not work outside of a cake component
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
    macro DefaultValueMacrosImpl.materializeDefaultValueImpl[DefaultValue.Aux[T, K, V], T, K, V]
  }
}

trait DefaultValueMacros extends ContextExtensions with SingletonTypeMacrosExtensions {
  import c.universe._

  def materializeDefaultValueImpl[S, T: WeakTypeTag, K: WeakTypeTag, V: WeakTypeTag]: Tree = {
    val kTpe = dealias(weakTypeOf[K])
    val fieldName = kTpe match {
      case SingletonSymbolTypeE(s) => s
      case _ => c.abort(c.enclosingPosition, s"Type $kTpe is not a tagged symbol type")
    }

    val tTpe = weakTypeOf[T]
    val tCompanionSym = companion(tTpe.typeSymbol)
    if (tCompanionSym == NoSymbol)
      c.abort(c.enclosingPosition, s"No companion symbol is available for type $tTpe")

    val ctorSym = decl(tTpe, names.CONSTRUCTOR).asTerm.alternatives.collectFirst {
      case ctor: MethodSymbol if ctor.isPrimaryConstructor => ctor
    }.getOrElse(c.abort(c.enclosingPosition, s"Could not find the primary constructor for type $tTpe"))

    val vTpe = weakTypeOf[V]

    val defaultMethodName = paramLists(ctorSym).headOption.flatMap { argSyms =>
      argSyms.map(_.asTerm).zipWithIndex.collect {
        case (p, i) if p.isParamWithDefault && p.name.toString == fieldName && p.typeSignature =:= vTpe =>
          termName(s"$$lessinit$$greater$$default$$${i+1}")  // TODO: not sure if this couldn't be made more correct
      }.headOption
    }

    val invocation = defaultMethodName match {
      case Some(name) => q"_root_.scala.Some($tCompanionSym.$name)"
      case None => q"_root_.scala.None"
    }

    val generatedClassName = typeName(s"DefaultValue$$${tTpe.typeSymbol.name}$$$fieldName")
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
