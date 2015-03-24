package io.github.netvl.picopickle

object BinaryVersionSpecificDefinitions {
  type Context = scala.reflect.macros.Context

  trait ContextExtensions {
    val c: Context

    def dealias(tpe: c.Type) = tpe.normalize

    def companion(sym: c.universe.Symbol) = sym.companionSymbol

    def termName(name: String) = c.universe.newTermName(name)
    def typeName(name: String) = c.universe.newTypeName(name)

    def decl(tpe: c.Type, tn: c.universe.TermName) = tpe.declaration(tn)
    def decls(tpe: c.Type) = tpe.declarations

    def paramLists(sym: c.universe.MethodSymbol) = sym.paramss

    def annotationType(ann: c.universe.Annotation) = ann.tpe

    def annotationArgs(ann: c.universe.Annotation) = ann.scalaArgs

    def names = c.universe.nme
  }

  trait SingletonTypeMacrosExtensions {
    val c: Context

    def unapplySingletonSymbolType(t: c.Type): Option[String]

    object SingletonSymbolTypeE {
      def unapply(t: c.Type): Option[String] = unapplySingletonSymbolType(t)
    }
  }

  trait LabelledMacrosExtensions {
    val c: Context

    def isProduct(t: c.Type): Boolean
    def isCoproduct(t: c.Type): Boolean
    def isCaseAccessorLike(s: c.universe.TermSymbol): Boolean
    def ctorsOf(t: c.Type): List[c.Type]
    def mkSingletonSymbolType(s: String): c.Type
    def mkSingletonSymbol(s: String): c.Tree
    def mkHListTpe(items: List[c.Type]): c.Type
    def nameAsString(name: c.Name): String
  }
}
