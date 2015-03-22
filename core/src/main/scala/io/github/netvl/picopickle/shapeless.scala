package io.github.netvl.picopickle

import shapeless._
import shapeless.labelled._

import scala.language.experimental.macros
import scala.annotation.StaticAnnotation
import reflect.macros.Context

import io.github.netvl.picopickle.SourceTypeTag.@@@

class key(k: String) extends StaticAnnotation

trait SealedTraitDiscriminatorComponent {
  def discriminatorKey: String
}

trait DefaultSealedTraitDiscriminatorComponent extends SealedTraitDiscriminatorComponent {
  override val discriminatorKey: String = "$variant"
}

trait AnnotationSupportingSymbolicLabellingComponent {
  implicit def mkSymbolicLabelling[T]: DefaultSymbolicLabelling[T] =
    macro AnnotationSupportSymbolicLabelling.mkDefaultSymbolicLabellingImpl[T]
}

object AnnotationSupportSymbolicLabelling {
  def inst(c: Context) = new AnnotationSupportSymbolicLabelling[c.type](c)

  def mkDefaultSymbolicLabellingImpl[T: c.WeakTypeTag](c: Context): c.Expr[DefaultSymbolicLabelling[T]] =
    c.Expr[DefaultSymbolicLabelling[T]](inst(c).mkDefaultSymbolicLabellingImpl[T])
}

// Extracted almost entirely from shapeless and tweaked to support custom annotations
class AnnotationSupportSymbolicLabelling[C <: Context](cc: C) extends LabelledMacros(cc) {
  import c.universe._

  override def mkDefaultSymbolicLabellingImpl[T](implicit tTag: WeakTypeTag[T]): Tree = {
    val tTpe = weakTypeOf[T]
    val labels: List[String] =
      if(isProduct(tTpe)) fieldSymbolsOf(tTpe).map(obtainKeyOfField(_, tTpe))
      else if(isCoproduct(tTpe)) ctorsOf(tTpe).map(obtainKeyOfType)
      else c.abort(c.enclosingPosition, s"$tTpe is not case class like or the root of a sealed family of types")

    val labelTpes = labels.map(SingletonSymbolType(_))
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

  def isKeyAnnotation(ann: Annotation): Boolean = ann.tpe =:= typeOf[key]

  def obtainKeyOfSym(sym: Symbol) = {
    sym.annotations
      .find(isKeyAnnotation)
      .flatMap(_.scalaArgs.headOption)
      .collect { case Literal(Constant(s)) => s.toString }
      .getOrElse(nameAsString(sym.name))
  }

  def obtainKeyOfType(tpe: Type): String = obtainKeyOfSym(tpe.typeSymbol)

  def obtainKeyOfField(sym: Symbol, tpe: Type): String = {
    tpe.declarations
      .collect { case d if d.name == nme.CONSTRUCTOR => d.asMethod }
      .flatMap(_.paramss.flatten)
      .filter(_.name == sym.name)  // don't know if this is a good idea but I see no other way
      .flatMap(_.annotations)
      .find(isKeyAnnotation)
      .flatMap(_.scalaArgs.headOption)
      .collect { case Literal(Constant(s)) => s.toString }
      .getOrElse(nameAsString(sym.name))
  }

  def fieldSymbolsOf(tpe: Type): List[TermSymbol] =
    tpe.declarations.toList collect {
      case sym: TermSymbol if isCaseAccessorLike(sym) => sym
    }
}

trait LowerPriorityShapelessWriters2 {
  this: BackendComponent with TypesComponent =>

  implicit def genericWriter[T, R](implicit g: LabelledGeneric.Aux[T, R], rw: Lazy[Writer[R]]): Writer[T] =
    Writer.fromPF1 {
      case (f, bv) => rw.value.write0(g.to(f), bv)
    }
}

trait LowerPriorityShapelessWriters extends LowerPriorityShapelessWriters2 {
  this: BackendComponent with TypesComponent =>

  implicit def fieldTypeWriter[K <: Symbol, V](implicit kw: Witness.Aux[K], vw: Lazy[Writer[V]]): Writer[FieldType[K, V]] =
    Writer.fromPF0 { f => {
      case Some(backend.Get.Object(v)) => backend.setObjectKey(v, kw.value.name, vw.value.write(f))
      case None => backend.makeObject(Map(kw.value.name -> vw.value.write(f)))
    }}
}

trait ShapelessWriters extends LowerPriorityShapelessWriters {
  this: BackendComponent with TypesComponent with SealedTraitDiscriminatorComponent =>

  implicit def optionFieldTypeWriter[K <: Symbol, V](implicit kw: Witness.Aux[K],
                                                     vw: Lazy[Writer[V]]): Writer[FieldType[K, Option[V]]] =
    Writer.fromPF0 { f => {
      case Some(backend.Get.Object(v)) => (f: Option[V]) match {
        case Some(value) => backend.setObjectKey(v, kw.value.name, vw.value.write(value))
        case None => v: backend.BValue
      }
      case None => (f: Option[V]) match {
        case Some(value) => backend.makeObject(Map(kw.value.name -> vw.value.write(value)))
        case None => backend.makeEmptyObject
      }
    }}

  implicit def recordHeadWriter[H, T <: HList](implicit hw: Lazy[Writer[H]], tw: Lazy[Writer[T]],
                                               ev: H <:< FieldType[_, _]): Writer[H :: T] =
    Writer.fromPF1 {
      case (h :: t, bv) => tw.value.write0(t, Some(hw.value.write0(h, bv)))
    }

  implicit val hnilWriter: Writer[HNil] =
    new Writer[HNil] {
      override def write0(value: HNil, acc: Option[backend.BValue]): backend.BValue = acc match {
        case Some(bv) => bv
        case None => backend.makeEmptyObject
      }
    }

  protected object ObjectOrEmpty {
    def unapply(bv: Option[backend.BValue]): Option[backend.BObject] = bv match {
      case Some(backend.Get.Object(obj)) => Some(obj)
      case None => Some(backend.makeEmptyObject)
      case _ => None
    }
  }
  
  implicit def coproductWriter[K <: Symbol, V, T <: Coproduct](implicit vw: Lazy[Writer[V]],
                                                               tw: Lazy[Writer[T]],
                                                               kw: Witness.Aux[K]): Writer[FieldType[K, V] :+: T] =
    Writer.fromPF1 {
      case (Inl(h), ObjectOrEmpty(obj)) =>
        vw.value.write0(h, Some(backend.setObjectKey(obj, discriminatorKey, backend.makeString(kw.value.name))))

      case (Inr(t), ObjectOrEmpty(obj)) =>
        tw.value.write0(t, Some(obj))
    }

  implicit val cnilWriter: Writer[CNil] =
    new Writer[CNil] {
      override def write0(value: CNil, acc: Option[backend.BValue]): backend.BValue = acc match {
        case Some(obj) => obj  // pass through the accumulated value
        // This is impossible, I believe
        case None => throw new IllegalStateException("Couldn't serialize a sealed trait")
      }
    }
}

trait LowerPriorityShapelessReaders2 {
  this: BackendComponent with TypesComponent =>

  implicit def genericCoproductReader[T, R <: Coproduct](implicit g: LabelledGeneric.Aux[T, R],
                                                         rr: Lazy[Reader[R]]): Reader[T] =
    Reader { case bv => g.from(rr.value.read(bv)) }

  implicit def genericHListReader[T, R <: HList, RT <: HList](implicit g: LabelledGeneric.Aux[T, R],
                                                              rt: TagWithType.Aux[R, T, RT],
                                                              rr: Lazy[Reader[RT]]): Reader[T] =
    Reader {
      case bv =>
        g.from(rt.unwrap(rr.value.read(bv)))
    }
}

trait LowerPriorityShapelessReaders extends LowerPriorityShapelessReaders2 {
  this: BackendComponent with TypesComponent with DefaultValuesComponent =>
  
//  implicit def fieldTypeReader[K <: Symbol, V](implicit kw: Witness.Aux[K],
//                                               vr: Lazy[Reader[V]]): Reader[FieldType[K, V]] =
//    Reader {
//      case backend.Get.Object(v) => field[K](vr.value.read(backend.getObjectKey(v, kw.value.name).get))
//    }

  implicit def fieldTypeReaderTagged[K <: Symbol, V, T](implicit kw: Witness.Aux[K],
                                                        vr: Lazy[Reader[V]],
                                                        dv: DefaultValue.Aux[T, K, V]): Reader[FieldType[K, V] @@@ T] =
    Reader {
      case backend.Get.Object(v) =>
        val value = backend.getObjectKey(v, kw.value.name).map(vr.value.read).orElse(dv.value)
        SourceTypeTag[T].attachTo(field[K](value.getOrElse(throw new IllegalStateException("Can't obtain value"))))
    }
}

trait ShapelessReaders extends LowerPriorityShapelessReaders {
  this: BackendComponent with TypesComponent with SealedTraitDiscriminatorComponent with DefaultValuesComponent =>
  
  implicit def optionFieldTypeReaderTagged[K <: Symbol, V, T](implicit kw: Witness.Aux[K],
                                                              vr: Lazy[Reader[V]],
                                                              dv: DefaultValue.Aux[T, K, V])
      : Reader[FieldType[K, Option[V]] @@@ T] =
    Reader {
      case backend.Get.Object(v) =>
        val value = backend.getObjectKey(v, kw.value.name).map(vr.value.read).orElse(dv.value)
        SourceTypeTag[T].attachTo(field[K](value))
    }

  implicit def recordHeadReader[H, T <: HList](implicit hr: Lazy[Reader[H]], tr: Lazy[Reader[T]],
                                               ev: H <:< FieldType[_, _]): Reader[H :: T] =
    Reader {
      case bv@backend.Get.Object(_) => hr.value.read(bv) :: tr.value.read(bv)
    }

  implicit val hnilReader: Reader[HNil] = Reader { case _ => HNil }

  protected object ObjectWithDiscriminator {
    def unapply(value: backend.BValue): Option[String] =
      backend.Extract.Object.unapply(value)
        .flatMap(_.get(discriminatorKey))
        .flatMap(backend.Extract.String.unapply)
  }

  implicit def coproductReader[K <: Symbol, V, T <: Coproduct](implicit vr: Lazy[Reader[V]], tr: Lazy[Reader[T]],
                                                               kw: Witness.Aux[K]): Reader[FieldType[K, V] :+: T] =
    Reader {
      case bv@ObjectWithDiscriminator(key) =>
        if (key == kw.value.name) Inl[FieldType[K, V], T](field[K](vr.value.read(bv)))
        else Inr[FieldType[K, V], T](tr.value.read(bv))
    }

  implicit val cnilReader: Reader[CNil] = Reader {
    // TODO: come up with better exceptions
    case ObjectWithDiscriminator(key) =>
      throw new IllegalArgumentException(s"Unknown discriminator: $key")
    case _ =>
      throw new IllegalArgumentException(s"Discriminator is unavailable")
  }
}

trait ShapelessReaderWritersComponent extends ShapelessReaders with ShapelessWriters {
  this: BackendComponent with TypesComponent with SealedTraitDiscriminatorComponent with DefaultValuesComponent =>
}
