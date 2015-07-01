package io.github.netvl.picopickle

import io.github.netvl.picopickle.BinaryVersionSpecificDefinitions.{ContextExtensions, LabelledMacrosExtensions}
import shapeless.DefaultSymbolicLabelling

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros

class discriminator(k: String) extends StaticAnnotation
class key(k: String) extends StaticAnnotation

trait SealedTraitDiscriminatorComponent {
  def defaultDiscriminatorKey: String

  trait Discriminator {
    type T
    def value: Option[String]
  }

  object Discriminator {
    type Aux[T0] = Discriminator { type T = T0 }
    implicit def materializeDiscriminator[T]: Discriminator.Aux[T] =
    macro DiscriminatorMacrosImpl.materializeDiscriminatorImpl[Discriminator.Aux[T], T]
  }
}

trait DefaultSealedTraitDiscriminatorComponent extends SealedTraitDiscriminatorComponent {
  override val defaultDiscriminatorKey: String = "$variant"
}

trait DiscriminatorMacros extends ContextExtensions {
  import c.universe._

  def materializeDiscriminatorImpl[S, T: WeakTypeTag]: Tree = {
    val tTpe = weakTypeOf[T]
    val tSym = tTpe.typeSymbol

    if (tSym.isClass && tSym.asClass.isSealed && tSym.asClass.isTrait) {  // sealed trait
      val discriminatorValue = tSym.annotations
        .find(isDiscriminatorAnnotation)
        .flatMap(annotationArgs(_).headOption)
        .collect { case Literal(Constant(s)) => s.toString }

      val discriminatorTree = discriminatorValue match {
        case Some(value) => q"_root_.scala.Some($value)"
        case None => q"_root_.scala.None"
      }

      val generatedClassName = typeName(s"Discriminator$$${tSym.name}")
      q"""
        {
          final class $generatedClassName extends Discriminator {
            type T = $tTpe
            def value: _root_.scala.Option[_root_.scala.Predef.String] = $discriminatorTree
          }
          new $generatedClassName
        }
       """
    } else {
      c.abort(c.enclosingPosition, "Discriminators can only be obtained for sealed traits")
    }
  }

  def isDiscriminatorAnnotation(ann: Annotation): Boolean = annotationType(ann) =:= typeOf[discriminator]
}

trait AnnotationSupportingSymbolicLabellingComponent {
  implicit def mkSymbolicLabelling[T]: DefaultSymbolicLabelling[T] =
    macro AnnotationSupportSymbolicLabellingImpl.mkDefaultSymbolicLabellingImpl[T]
}

// Extracted almost entirely from shapeless and tweaked to support custom annotations
trait AnnotationSupportSymbolicLabelling extends ContextExtensions with LabelledMacrosExtensions {
  import c.universe._

  def mkDefaultSymbolicLabellingImpl[T](implicit tTag: WeakTypeTag[T]): Tree = {
    val tTpe = weakTypeOf[T]
    val labels: List[String] =
      if (isProduct(tTpe)) fieldSymbolsOf(tTpe).map(obtainKeyOfField(_, tTpe))
      else if (isCoproduct(tTpe)) ctorsOf(tTpe).map(obtainKeyOfType)
      else c.abort(c.enclosingPosition, s"$tTpe is not case class like or the root of a sealed family of types")

    val labelTpes = labels.map(mkSingletonSymbolType)
    val labelValues = labels.map(mkSingletonSymbol)

    val labelsTpe = mkHListTpe(labelTpes)
    val labelsValue =
      labelValues.foldRight(q"_root_.shapeless.HNil": Tree) {
        case (elem, acc) => q"_root_.shapeless.::($elem, $acc)"
      }

    q"""
      new _root_.shapeless.DefaultSymbolicLabelling[$tTpe] {
        type Out = $labelsTpe
        def apply(): $labelsTpe = $labelsValue
      }
    """
  }

  def isKeyAnnotation(ann: Annotation): Boolean = annotationType(ann) =:= typeOf[key]

  def obtainKeyOfSym(sym: Symbol) = {
    sym.annotations
      .find(isKeyAnnotation)
      .flatMap(annotationArgs(_).headOption)
      .collect { case Literal(Constant(s)) => s.toString }
      .getOrElse(nameAsString(sym.name))
  }

  def obtainKeyOfType(tpe: Type): String = obtainKeyOfSym(tpe.typeSymbol)

  def obtainKeyOfField(sym: Symbol, tpe: Type): String = {
    decls(tpe)
      .collect { case d if d.name == names.CONSTRUCTOR => d.asMethod }
      .flatMap(paramLists(_).flatten)
      .filter(_.name == sym.name)  // don't know if this is a good idea but I see no other way
      .flatMap(_.annotations)
      .find(isKeyAnnotation)
      .flatMap(annotationArgs(_).headOption)
      .collect { case Literal(Constant(s)) => s.toString }
      .getOrElse(nameAsString(sym.name))
  }

  def fieldSymbolsOf(tpe: Type): List[TermSymbol] =
    decls(tpe).toList collect {
      case sym: TermSymbol if isCaseAccessorLike(sym) => sym
    }
}
