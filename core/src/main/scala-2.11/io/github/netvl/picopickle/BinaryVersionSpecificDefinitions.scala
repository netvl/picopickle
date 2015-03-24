package io.github.netvl.picopickle

object BinaryVersionSpecificDefinitions {
  type Context = scala.reflect.macros.whitebox.Context

  trait ContextExtensions {
    val c: Context

    def dealias(tpe: c.Type) = tpe.dealias

    def companion(sym: c.universe.Symbol) = sym.companion

    def termName(name: String) = c.universe.TermName(name)
    def typeName(name: String) = c.universe.TypeName(name)

    def decl(tpe: c.Type, tn: c.universe.TermName) = tpe.decl(tn)
    def decls(tpe: c.Type) = tpe.decls

    def paramLists(sym: c.universe.MethodSymbol) = sym.paramLists

    def annotationType(ann: c.universe.Annotation) = ann.tree.tpe

    def annotationArgs(ann: c.universe.Annotation) = ann.tree.children.tail

    def names = c.universe.termNames
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
