picopickle 0.0.2
================

(This readme is currently in flux and may reflect features unavailable in the latest released version.
This situation is only temporary)

picopickle is a serialization library for Scala. Its main features are:

* Small and almost dependency-less (the core library depends only on [shapeless]).
* Extensibility: you can define your own serializators for your types and you can create
  custom *backends*, that is, you can use the same library for the different serialization formats
  (collections, JSON, BSON, etc.); other parts of the serialization behavior like nulls handling
  can also be customized.
* Flexibility and convenience: default serialization format is fine for most uses, but it can
  be customized almost arbitrarily with the support of a convenient converters DSL.
* Static serialization without reflection: shapeless [`Generic`][Generic] macros are used to
  provide serializers for arbitrary types, which means that no reflection is used.

  [shapeless]: https://github.com/milessabin/shapeless
  [Generic]: https://github.com/milessabin/shapeless/wiki/Feature-overview:-shapeless-2.0.0#generic-representation-of-sealed-families-of-case-classes

Getting started
---------------

The library is published to the Maven central, so you can just add the following line
to your `build.sbt` file in order to use the core library:

```scala
libraryDependencies += "io.github.netvl.picopickle" %% "picopickle-core" % "0.0.2"
```

The library is compiled for both 2.10 and 2.11 Scala versions. If you use 2.10, however,
you will need to add [Macro Paradise] compiler plugin because shapeless macros depend on it:

```scala
libraryDependencies += compilerPlugin("org.scalamacros" %% "paradise" % "2.0.1" cross CrossVersion.full)
// or
addCompilerPlugin("org.scalamacros" %% "paradise" % "2.0.1" cross CrossVersion.full)
```

Scala 2.11 users do not need this as all relevant macro support is already present in 2.11.

  [Macro Paradise]: http://docs.scala-lang.org/overviews/macros/paradise.html

### Backend dependencies

Picopickle supports different *backends*. A backend defines the target serialization format,
for example, JSON, BSON or just regular collections. The core library provides collections
backend, and an additional JSON backend based on [Jawn] parser is available as
`picopickle-backend-jawn`:

```scala
libraryDependencies += "io.github.netvl.picopickle" %% "picopickle-backend-jawn" % "0.0.2"
```

Jawn backend uses Jawn parser (naturally!) to read JSON strings but it uses custom renderer
to print JSON AST as a string in order to keep dependencies to the minimum. This renderer
is very basic and does not support pretty-printing; this is something which is likely to be
fixed in one of the future versions.

You can create your own backends to support your own data formats; more information on how
to do it is available below. It is likely that more officially supported backends will be
available later.

  [Jawn]: https://github.com/non/jawn

Serialization mechanism
-----------------------

picopickle uses the pretty standard typeclass approach where the way the type is serialized
or deserialized is defined through implicit objects (called `Reader[T]` and `Writer[T]` in picopickle)
in scope. The library defines corresponding instances for a lot of standard types:

* primitives and other basic types: `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `Boolean`,
  `Char`, `String`, `Unit`, `Null`, `Symbol`, `Option[T]`, `Either[A, B]`;
* tuples (currently generated as a part of build process for lengths from 1 to 22);
* most of standard Scala collections;
* sealed trait hierarchies: case classes and case objects, possibly implementing some sealed trait,
  and the sealed trait itself.

Serializers for sealed trait hierarchies are derived automatically with the help of shapeless
`LabelledGeneric` type class. The library defines several generic instances for the core shapeless
types (`HList` and `Coproduct`), and shapeless does the hard work of inspecting case classes
and sealed traits.

Since sealed trait hierarchies are equivalent to algebraic data types, their representation
with the shapeless type is fairly natural: each case class/case object is represented
by a `HList` of corresponding field types labelled with field names, and the whole hierarchy
is represented by a `Coproduct` of the corresponding types which implement the sealed trait.

picopickle also supports recursive types, that is, when a case class eventually depends on
itself or on the sealed trait it belongs to, for example:

```scala
sealed trait Root
case object A extends Root
case class B(x: Int, b: Option[B]) extends Root  // depends on itself
case class C(next: Root) extends Root  // depends on the sealed trait
```

picopickle also supports default values in case classes and renaming of fields or sealed trait descendants
with a bit of custom macros.

Currently picopickle does not support case classes with variable arguments, because they are
not supported by shapeless `Generic`. This is going to change when the next shapeless version
is released.

Usage
-----

### Basic usage

picopickle is structured using the cake pattern, that is, it consists of several traits providing
parts of the functionality which are then combined into a single object called a *pickler*. It
provides everything necessary for the serialization via a glob import:

```scala
import some.package.SomePickler._

write("Hello")
```

The core library and the Jawn backend library provide default picklers, so if you don't need
any customization (e.g. you don't need to define custom serializers for your types) you can just
import the internals of one of these picklers:

```scala
import io.github.netvl.picopickle.backends.collections.CollectionsPickler._

case class A(x: Int, y: String)

assert(write(A(10, "hi")) == Map("x" -> 10, "y" -> "hi"))
```

Jawn-based pickler also provides additional functions, `readString()`/`writeString()` and
`readAst()`/`writeAst()`, which [de]serialize objects to strings and JSON AST to strings,
respectively:

```scala
import io.github.netvl.picopickle.backends.jawn.JsonPickler._

case class A(x: Int, y: String)

assert(writeString(A(10, "hi")) == """{"x":10,"y":"hi"}""")
```

Currently the string JSON representation is not prettified (but prettification may be implemented in later versions).

### Custom picklers

It is possible that you would want to define custom serializers for some of your
types. In that case you can define custom serializer instances in a trait which "depends" on
`BackendComponent` and `TypesComponent`:

```scala
import io.github.netvl.picopickle.{BackendComponent, TypesComponent}

case class DefinedByInt(x: Int, y: String)

trait CustomSerializers {
  this: BackendComponent with TypesComponent =>

  implicit val definedByIntWriter: Writer[DefinedByInt] = Writer {
    case DefinedByInt(x, _) => backend.makeNumber(x)
  }

  implicit val definedByIntReader: Reader[DefinedByInt] = Reader {
    case backend.Extract.Number(x) => DefinedByInt(x.intValue(), x.intValue().toString)
  }
}
```

Then this trait should be mixed into the corresponding pickler trait conveniently defined
in the library in order to create the pickler object:

```scala
import io.github.netvl.picopickle.backends.jawn.JsonPickler

object CustomPickler extends JsonPickler with CustomSerializers
```

You can also define the serializers directly in the pickler object if they are not supposed
to be reused or if you only have one pickler object in your program:

```scala
import io.github.netvl.picopickle.backends.jawn.JsonPickler

object CustomPickler extends JsonPickler {
  implicit val definedByIntWriter: Writer[DefinedByInt] = Writer {
    case DefinedByInt(x, _) => backend.makeNumber(x)
  }

  implicit val definedByIntReader: Reader[DefinedByInt] = Reader {
    case backend.Extract.Number(x) => DefinedByInt(x.intValue(), x.intValue().toString)
  }
}
```

picopickle provides several utilities which help you writing custom serializers and deserializers; at first, however,
we need to explain what backends are.

### Backends

A *backend* in picopickle defines an intermediate AST called *backend representation* which is the media
which values of other types can be serialized into. For example, for JSON it is JSON AST, that is, a set of classes
which together can be used to form any correct JSON object tree. Additionally, a backend provides
methods to construct these values generically from basic Scala types and collections and to deconstruct
these values back into these basic types.

In general, a backend may be arbitrarily complex. It can consist of a lot of classes with various relationships
between them and all the necessary methods to construct them. However, in order to provide the ability to
serialize arbitrary types to arbitrary backend representations, some restrictions should be put on the structure
of the backend representation, that is, there should be some minimal set of primitives which should be supported
by all backends. picopickle requires that all backends support basic JSON-like tree AST, that is,
objects keyed by strings, arrays indexed by integers, strings, numbers, booleans and null. Using these primitives,
picopickle is able to provide serializers for basic primitive types and sealed trait hierarchies out of the box.

`Backend` trait is used to represent backends in Scala code. This trait contains abstract types which
define the AST and a lot of methods to construct the AST from basic types. Each implementation of this trait
should provide the following abstract types:

```scala
  type BValue
  type BObject <: BValue
  type BArray <: BValue
  type BString <: BValue
  type BNumber <: BValue
  type BBoolean <: BValue
  type BNull <: BValue
```

Also each implementation must provide a set of methods for converting between these abstract types and basic Scala
types. The mapping is as follows:

```
  BObject  -> Map[String, BValue]
  BArray   -> Vector[BValue]
  BString  -> String
  BNumber  -> Number
  BBoolean -> Boolean
  BNull    -> Null
```

That is, each backend should provide methods to convert from `BValue` to `Map[String, BValue]` and back etc. These
methods can be divided into three groups:

* those which convert Scala values to backend representation: prefixed with `make`;
* those which convert backend representation to Scala values: prefixed with `from`;
* those which extract concrete backend type (e.g. `BObject`, `BString`) from the abstract `BValue`: prefixed with `get`.

The last group of methods return `Option[<corresponding type>]` because they are partial in their nature.

There are also some convenience methos like `makeEmptyObject` or `getArrayValueAt` which can be defined via
a conversion with the corresponding `from` method and then a query on the resulting Scala object, but these
methods may query the underlying backend representation directly, saving on the intermediate objects construction.

In order to create a custom backend you need to implement `Backend` trait first:

```scala
object MyBackend extends Backend {
  type BValue = ...
  ...
}
```

Then you need to create a cake component for this backend; this component must implement `BackendComponent` trait:

```scala
trait MyBackendComponent extends BackendComponent {
  override val backend = MyBackend
}
```

And finally you should extend `DefaultPickler`, mixing it with your backend component:

```scala
trait MyPickler extends DefaultPickler with MyBackendComponent
object MyPickler extends MyPickler
```

Naturally, you can choose not to merge the `DefaultPickler` fully into your pickler if you don't want to, for example,
if you don't need the automatic writers materialization for sealed trait hierarchies. In that case you can
mix only those traits you need. See `DefaultPickler` documentation to find out which components it consists of
(**TODO**).

After this `MyPickler.read` and `MyPickler.write` methods will work with your backend representation.

#### Instantiating custom serializers

picopickle defines `Writer` and `Reader` basic types in `TypesComponent` which are called *serializers*. 
They are responsible for converting arbitrary types to their backend representation and back, respectively. 
The most basic way to construct custom serializers is to use `apply` method on `Reader` and `Writer` 
companion objects, which take `PartialFunction[backend.BValue, T]` and `PartialFunction[T, backend.BValue]`, 
respectively (you can find an example of both above).

Despite that `Writer` takes a partial function, it still should be able to serialize any values
of its corresponding type. `Reader`, however, can fail to match the backend representation. Currently
it will usually lead to a `MatchError` thrown by the `read()` call; this is going to improve in future.

`TypesComponent` also defines a combined serializer called `ReadWriter`:

```scala
type ReadWriter[T] = Reader[T] with Writer[T]
```

Its companion object also provides convenient facilities to create its instances. The example above can be
rewritten with `ReadWriter` like this:

```scala
implicit val definedByIntReadWriter: ReadWriter[DefinedByInt] = ReadWriter.reading {
  case backend.Extract.Number(x) => DefinedByInt(x.intValue(), x.intValue().toString)
}.writing {
  case DefinedByInt(x, _) => backend.makeNumber(x)
}
```

You can switch `reading`/`writing` branches order if you like.

#### Extractors and backend conversion implicits

`Backend` trait provides methods to create and deconstruct objects of backend representation: these are `make*`,
`from*` and `get*` methods described above. To simplify writing custom serializers, however, picopickle
provides a set of tools which help you writing conversions. The most basic of them are *extractors* and
*backend conversion implicits*.

Backend object contains several singleton objects with `unapply` methods which can be used to pattern-match
on `backend.BValue` and obtain the low-level values out of it, for example, to get a `Map[String, backend.BValue]`
out of `backend.BObject`, if this particular `backend.BValue` which you're matching on indeed is a `backend.BObject`:

```scala
backend.makeObject(...) match {
  case backend.Extract.Object(m) =>  // m is of type Map[String, backend.BValue]
}
```

There are extractors for all of the main backend representation variants:
 * `backend.Extract.Object`
 * `backend.Extract.Array`
 * `backend.Extract.String`
 * `backend.Extract.Number`
 * `backend.Extract.Boolean`

Their `unapply` implementation simply calls corresponding `get*` and `from*` methods, like this:

```scala
object Extractors {
  object String {
    def unapply(value: BValue): Option[String] = getString(value).map(fromString)
  }
}
```

The opposite conversion (from primitives to the backend representation) can be done with `make*` methods on the
backend, but picopickle also provides a set of implicit decorators which provide `toBackend` method on all of
the basic types. These decorators are defined in `backend.conversionImplicits` object:

```scala
import backend.conversionImplicits._

val s: backend.BString = "hello world".toBackend

// the above is equivalent to this:

val s: backend.BString = backend.makeString("hello world")
```

These implicit methods are somewhat more convenient than `make*` functions.

Converters
----------

Low-level conversions, however, may be overly verbose to write. picopickle provides a declarative way of
defining how the backend representation should be translated to the desired Scala objects and vice versa.
This is done with *converters*.

A converter looks much like a `ReadWriter`; however, it is parameterized by two types, source and target:

```scala
trait Converter[-T, +U] {
  def toBackend(v: T): backend.BValue
  def isDefinedAt(bv: backend.BValue): Boolean
  def fromBackend(bv: backend.BValue): U
}
```

The converters library defines several implicit conversions which allow any converter to be used as the
corresponding `Reader`, `Writer` or `ReadWriter`:

```scala
Converter[T, _] -> Writer[T]
Converter[_, U] -> Reader[U]
Converter[T, T] -> ReadWriter[T]
```

A converter which consumes and produces the same type is called an *identity* converter for that type. Naturally,
only identity converters can be used as `ReadWriter`s. Identity converters have a convenient type alias
`Converter.Id[T]`.

Converters library also defines several combinators on converters which allow combining them to obtain new
converters, and it also provides built-in converters for basic primitive types and objects and arrays.

For example, here is how you can define a conversion for some case class manually:

```scala
case class A(a: Boolean, b: Double)

trait CustomSerializers extends JsonPickler {
  import shapeless._
  import converters._

  val aConverter: Converter.Id[A] = unlift(A.unapply) >>> obj {
    "a" -> bool ::
    "b" -> num.double ::
    HNil
  } >>> A.apply _

  val aReadWriter: ReadWriter[A] = aConverter  // an implicit conversion is used here
}
```

Here `obj.apply` is used to define an identity converter for `Boolean :: Double :: HNil`,
and `>>>` operations "prepend" and "append" a deconstructor and a constructor for class `A`:

```scala
A.unapply          : A => Option[(Boolean, Double)]
unlift(A.unapply)  : A => (Boolean, Double)

A.apply _          : (Boolean, Double) => A

obj {
  "a" -> bool ::
  "b" -> num.double ::
  HNil
}                  : Converter.Id[Boolean :: Double :: HNil]
```

`bool` and `num.double` are identity converters for `Boolean` and `Double`, respectively.

`>>>` operations employ a little of shapeless magic to convert the functions like the ones above to functions
which consume and produce `HList`s. There is also `>>` combinator which does not use shapeless and "prepends"
and "appends" a function of corresponding type directly:

```scala
(A => B) >> Converter[B, C] >> (C => D)  ->  Converter[A, D]

// compare:

(A => (T1, T2, ..., Tn)) >>> Converter.Id[T1 :: T2 :: ... :: Tn :: HNil] >>> ((T1, T2, ..., Tn) => A)  ->  Converter.Id[A]
```

Note that this is very type-safe. For example, if you get the order or the types of fields in `obj` wrong, it won't compile.

picopickle additionally provides a convenient implicit alias for `andThen` on functions, also called `>>`. Together with 
`>>`  on converters this allows writing chains of transformations easily. For example, suppose you have an object which 
can be represented as an array of bytes. Then you want to serialize this byte array as a string in Base64 encoding. 
This can be written as follows:

```scala
import java.util.Base64
import java.nio.charset.StandardCharsets

case class Data(s: String)
object Data {
  def asBytes(d: Data) = d.s.getBytes(StandardCharsets.UTF_8)
  def fromBytes(b: Array[Byte]) = Data(new String(b, StandardCharsets.UTF_8))
}

val dataReadWriter: ReadWriter[Data] =
  Data.asBytes _ >>
  Base64.getEncoder.encodeToString _ >>
  str >>
  Base64.getDecoder.decode _ >>
  Data.fromBytes _
```

The sequence of functions chained with `>>` naturally defines the transformation order in both directions.

Similar thing is also possible for arrays. For example, you can serialize your case class as an array
of fields:

```scala
val aReadWriter: ReadWriter[A] = unlift(A.unapply) >>> arr(bool :: num.double :: HNil) >>> A.apply _
```

Naturally, there are converters for homogeneous arrays and objects too - they allow mapping to Scala collections:

```scala
val intListConv: Converter.Id[List[Int]] = arr.as[List].of(num.int)
val vecTreeMapConv: Converter.Id[TreeMap[String, Vector[Double]]] = obj.as[TreeMap].to(arr.as[Vector].of(num.double))
```

There is also a converter which delegates to `Reader` and `Writer` if corresponding implicit instances are available:

```scala
val optionStringConv: Converter.Id[Option[String]] = value[Option[String]]
```

You can find more on converters in their Scaladoc section (**TODO**).

Supported types
---------------

By default picopickle provides a lot of serializers for various types which do their
best to represent their respective types in the serialized form as close as possible.
These serializers are then mixed into a single pickler.

The serializers are defined in a couple of traits:

```
io.github.netvl.picopickle.{CollectionReaders, CollectionWriters, CollectionReaderWritersComponent}
io.github.netvl.picopickle.{ShapelessReaders, ShapelessWriters, ShapelessReaderWritersComponent}
io.github.netvl.picopickle.{PrimitiveReaders, PrimitiveWriters, PrimitiveReaderWritersComponent}
io.github.netvl.picopickle.{TupleReaders, TupleWriters, TupleReaderWritersComponent}  // generated automatically
```

Every serializer is an overloadable `def` or `val`, so you can easily customize serialization
format by overriding the corresponding implicit definition with your own one.

Examples below use `JsonPickler`, so it is implicitly assumed that something like

```scala
import io.github.netvl.picopickle.backends.jawn.JsonPickler._
```

is present in the code.

### Primitives and basic types

picopickle natively supports serialization of all primitive and basic types:

```scala
  writeString(1: Int)       shouldEqual "1"
  writeString(2L: Long)     shouldEqual "2"
  writeString(12.2: Double) shouldEqual "3"
  writeString('a')          shouldEqual "\"a\"
  writeString("hello")      shouldEqual "\"hello\""
  writeString(true)         shouldEqual "true"
  writeString(false)        shouldEqual "false"
  writeString(null)         shouldEqual "null"
  writeString('symbol)      shouldEqual "\"symbol\""
```

By default characters are serialized as strings, but, for example, collections backend redefines this behavior.

picopickle also can serialize `Option[T]` and `Either[L, R]` as long as there are serializers for their type
parameters:

```scala
  writeString(Some(1)) shouldEqual "[1]"
  writeString(None)    shouldEqual "1"

  writeString(Left("hello"))   shouldEqual """[0,"hello"]"""
  writeString(Right('goodbye)) shouldEqual """[1,"goodbye"]"""
```

Optional values are also handled specially when they are a part of case class definition; see below for more
explanation.

Please note that `Either[L, R]` serialization format is not final and can change in future versions.

### Numbers and accuracy

Most JSON libraries represent numbers as 64-bit floats, i.e. `Double`s, but some numerical values do not fit into
`Double`, and rounding occurs:

```scala
80000000000000000.0 shouldEqual 80000000000000008.0   // does not throw
```

In order to represent numbers as accurately as possible picopickle by default serializes all `Long`s which
cannot be represented as `Double` precisely as strings:

```scala
  writeString(80000000000000000L)      shouldEqual "\"80000000000000000\""
  writeString(Double.PositiveInfinity) shouldEqual "Infinity"
```

The same mechanism will probably be used when `BigInt`/`BigDecimal` handlers will be added.

### Tuples

Tuples are serialized as arrays:

```scala
  writeString((1, true, "a"))  shouldEqual "[1,true,\"a\"]"
```

The only exception is a tuple of zero items, usually called `Unit`. It is serialized as an empty object:

```scala
  writeString(())           shouldEqual "{}"
```

Naturally, all elements of tuples must be serializable as well.

Tuple serializer instances are generated as a part of build process, and currently only
tuples with the length up to and including 22 are supported.

### Collections

Most of Scala collections library classes are supported, including all of the abstract ones below the `Iterable`,
as well as arrays:

```scala
  writeString(Iterable(1, 2, 3, 4))    shouldEqual "[1,2,3,4]"
  writeString(Seq(1, 2, 3, 4))         shouldEqual "[1,2,3,4]"
  writeString(Set(1, 2, 3, 4))         shouldEqual "[1,2,3,4]"
  writeString(Map(1 -> 2, 3 -> 4))     shouldEqual "[[1,2],[3,4]]"

  writeString(1 :: 2 :: 3 :: Nil)      shouldEqual "[1,2,3]"
  writeString(Vector(1, 2, 3))         shouldEqual "[1,2,3]"
  wrtieString(TreeMap(1 -> 2, 3 -> 4)) shouldEqual "[[1,2],[3,4]]"

  writeString(Array(1, 2, 3))          shouldEqual "[1,2,3]"
```

Mutable collections can be [de]serialized as well.

Maps are serialized like iterables of two-element tuples, that is, into arrays of two-element arrays. However,
if the map has string keys (which is determined statically), it will be serialized as an object:

```scala
  writeString(Map("a" -> 1, "b" -> 2)) shouldEqual """{"a":1,"b":2}"""
```

If you're using abstract collection types like `Seq`, `Set` or `Map`, picopickle will work flawlessly. If you
use concrete collection types, however, there could be problems. picopickle has a lot of instances for most of
the main concrete implementations, but not for all of them. If you need something which is not present in the
library, feel free to file an issue.

### Sealed trait hierarchies

picopickle supports automatic serialization of sealed trait hierarchies (STH), that is, case classes, probably
inheriting a sealed trait. In other words, picopickle can serialize algebraic data types.

The most trivial examples of STH are standalone case objects and case classes:

```scala
  case object A
  case class B(x: Int, y: A)

  writeString(A)        shouldEqual "{}"
  writeString(B(10, A)) shouldEqual """{"x":10,"y":{}}"""
```

By default picopickle serializes case classes as objects with keys being the names of the fields. Case objects
are serialized as empty objects.

Case classes and objects can have a sealed trait as their parent:

```scala
  sealed trait Root
  case object A extends Root
  case class B(x: Int, y: Boolean)
  case class C(name: String, y: Root) extends Root
```

When you explicitly set the serialized type to `Root` (or pass a value of type `Root` but not of some concrete
subclass), it will be serialized as an object with a *discriminator key*:

```scala
  writeString[Root](A)           shouldEqual """{"$variant":"A"}"""
  writeString[Root](B(10, true)) shouldEqual """{"$variant":"B","x":10,"y":true}"""
  writeString[Root](C("me", A))  shouldEqual """{"$variant":"C","name":"me","y":{"$variant":"A"}}"""
```

If you don't request `Root` explicitly, the classes will be serialized as if they are not a part of an STH:

```scala
  writeString(B(10, true)) shouldEqual """{"x":10,"y":true}"""
```

Usually this is not a problem, however, because if you are working with a sealed trait, you usually have variables
of its type, not of its subtypes.

Sealed trait hierarchies serialization is implemented using shapeless `LabelledGeneric` implicitly materialized
instances and a bit of custom macros which handle field renaming and default values (both are not supported by 
shapeless natively).

### Changing the discriminator key

You can customize the discriminator key used by shapeless serializers by overriding
`discriminatorKey` field defined in `io.github.netvl.picopickle.SealedTraitDiscriminator` trait
(its default value is `"$variant"`):

```scala
  object CustomPickler extends JsonPickler {
    override val discriminatorKey = "$type"
  }

  // STH is from the example above
  writeString[Root](A) shouldEqual """{"$type":"A"}"""
```

Of course, you can extract it into a separate trait and mix it into different picklers if you want.

### Serialization of optional fields

If a case class has a field of type `Option[T]`, then this field is serialized in a different way than
a regular option: if the value of the field is `None`, then the corresponding key will be absent from the serialized
data, and if it is `Some(x)`, then the key will be present and its value will be just `x`, without an additional
layer of an array:

```scala
  case class A(name: String, x: Option[Long])

  writeString(A("absent"))            shouldEqual """{"name":"absent"}"""
  writeString(A("present", Some(42L)) shouldEqual """{"name":"present","x":42}"""
```

This allows easy evolution of your data structures - you can always add an `Option`al field and the data serialized
before this update will still be deserialized correctly, putting a `None` into the new field.

If an optional field again contains an option:

```scala
  case class A(x: Option[Option[Long]])
```

then the "outer" option is serialized as described in the above paragraph while the "inner" option is serialized
as a possibly empty array, just like options are serialized in other contexts:

```scala
  writeString(A(None))            shouldEqual """{}"""
  writeString(A(Some(None)))      shouldEqual """{"x":[]}"""
  writeString(A(Some(Some(10L)))) shouldEqual """{"x":[10]}"""
```

### Renaming fields and sealed trait variants

picopickle also provides an ability to rename fields and STH variant labels. This can be done by annotating
fields with `@key` annotation:

```scala
  import io.github.netvl.picopickle.key

  sealed trait Root
  @key("0") case object A
  @key("1") case class B(@key("a") x: Int, @key("b") y: Boolean)

  writeString[Root](A)            shouldEqual """{"$variant":"0"}"""
  writeString[Root](B(10, false)) shouldEqual """{"$variant":"1","a":10,"b":false}"""
```

Keys always are strings, though.

### Default values of case class fields

picopickle also respects default values defined in a case class, which simplifies changes in your data classes
even more. If a field has a default value and the serialized object does not contain the corresponding field,
the default value will be used:

```scala
  case class A(n: Int = 11)

  readString[A]("""{"n":22}""") shouldEqual A(22)
  readString[A]("""{}""")       shouldEqual A()
```

As you can see, this mechanism naturally interferes with the optional fields handling. picopickle resolves 
this conflict in the following way: if no value is present at the corresponding key and a default value is 
set for the field, then it takes precedence over option handling. This affects a rather rare case when there 
is an optional field with a default value other than `None`:

```scala
  case class A(n: Option[Int] = Some(10))

  readString[A]("{}") shouldEqual A(Some(10))  // not A(None)
```

This is what usually expected in such situation.

### Varargs

Currently picopickle does not support reading or writing case classes with variable arguments because shapeless
`Generic` (and thus `LabelledGeneric`) do not support such classes in 2.1.0. This is already fixed in shapeless master,
and when the next shapeless version is released, picopickle will be able to handle such classes as well.

### Nulls

`null` value, as is widely known, tends to cause problems, and it is discouraged in idiomatic Scala code.
Unfortunately, sometimes you need to interact with external systems which do use nulls. JSON has null value as well.
Because of this picopickle supports nulls (it even has `BNull` as one of the fundamental backend types) but
it also provides means to control how nulls should be handled.

`Reader` and `Writer` traits do not contain any special logic to handle nulls. Instances of `Reader` and `Writer`
created through their companion objects, however, do have such logic: they delegate null handling to a `NullHandler`
instance provided by `NullHandlerComponent`. `NullHandler` is a trait of the following structure:

```scala
  trait NullHandler {
    def handlesNull: Boolean
    def toBackend[T](value: T, cont: T => backend.BValue): backend.BValue
    def fromBackend[T](value: backend.BValue, cont: backend.BValue => T): T
  }
```

That is, it is some kind of a preprocessor which inspects the passed value for nulls and can [de]serialize them
specially or prohibit the [de]serialization at all.

By default picopickle allows nulls everywhere (`DefaultPickler` includes `DefaultNullHandlerComponent`). That is,
if a null is serialized, it will be represented unconditionally with `backend.BNull`, and `backend.BNull` will
be deserialized (again, unconditionally) as a `null`.

There is another `NullHandlerComponent` implementation, namely `ProhibitiveNullHandlerComponent`, which disallows
serialization of nulls, throwing an exception if it encounters a null value either in Scala object or in a
backend object. If you don't need to keep compatibility with some external system which uses null values then 
it may be sensible to extend the desired pickler, overriding the default null handler:

```scala
trait MyJsonPickler extends JsonPickler with ProhibitiveNullHandlerComponent
```

As long as you use `Reader`/`Writer` companion objects or converters to create your custom serializers,
the null handling behavior will be consistent for all types handled by your pickler.

### Accurate numbers serialization

Some backends do not allow serializing some numbers accurately. For example, most JSON implementations
represent all numbers with 64-bit floating point numbers, i.e. `Double`s. Scala `Long`, for example,
can't be represented accurately with `Double`. This is even more true for big integers and decimals.

picopickle backends provide means to serialize arbitrary numbers as accurately as possible with these methods:

```scala
  def makeNumberAccurately(n: Number): BValue
  def fromNumberAccurately(value: BValue): Number
```

You can see that these methods take and return `BValue` instead of `BNumber`. Backend implementations can take
advantage of this and serialize long numbers as strings or in some other format in order to keep the precision.
Built-in serializers for numbers use these methods by default.

picopickle also provides a special trait, `DoubleOrStringNumberRepr`, which provides methods to store a number
as a `BNumber` if it can be represented precisely in `Double` as a `BString` otherwise.
This trait is useful e.g. when writing a JSON-based backend.

Official backends
-----------------

### Collections pickler

picopickle has several "official" backends. One of them, provided by `picopickle-core` library, allows serialization
into a tree of collections. This backend is available immediately with only the `core` dependency:

```scala
libraryDependencies += "io.github.netvl.picopickle" %% "picopickle-core" % "0.0.2"
```

In this backend the following AST mapping holds:

```
BValue   -> Any
BObject  -> Map[String, Any]
BArray   -> Vector[Any]
BString  -> String
BNumber  -> Number
BBoolean -> Boolean
BNull    -> Null
```

In this backend the backend representation coincide with the target media, so no conversion methods except the
basic `read`/`write` are necessary.

This backend also tweaks the default `Char` serializer to write and read characters as `Char`s, not
as `String`s (which is the default behavior).

Note that everything else, even other collections, are still serialized as usual, so, for example, tuples are
represented as vectors and maps are represented as vectors of vectors:

```scala
write((2: Int, "abcde": String))  ->  Vector(2, "abcde")
write(Map(1 -> 2, 3 -> 4))        ->  Vector(Vector(1, 2), Vector(3, 4))
```

### JSON pickler

Another official backend is used for conversion to and from JSON. JSON parsing is done with [jawn] library;
JSON rendering, however, is custom. This backend is available in `picopickle-backend-jawn`:

```scala
libraryDependencies += "io.github.netvl.picopickle" %% "picopickle-backend-jawn" % "0.0.2"
```

This backend's AST is defined in `io.github.netvl.picopickle.backends.jawn.JsonAst` and consists of several
basic case classes corresponding to JSON basic types. No additional utilities for JSON manipulation are provided;
you should use another library if you want this.

JSON backend additionally provides two sets of methods: `readAst`/`writeAst`, which convert JSON AST from and to the
JSON rendered as a string, and `readString`/`writeString`, which [de]serialize directly from and to a string.
Usually the last pair of methods is what you want to use when you want to work with JSON serialization.

No support for streaming serialization is available and is not likely to appear in the future because of the
abstract nature of backends (not every backend support streaming, for example, collections backend doesn't) and
because it would require completely different architecture.

Error handling
--------------

While serialization is straightforward and should never fail (if it does, it is most likely a bug in the library
or in some `Writer` implementation), deserialization is prone to errors because the serialized representation usually
has free-form structure and is not statically mapped on its Scala representation. However, picopickle does not
currently have special handling for deserialization errors. Expect arbitrary `NoSuchElementException`s and
`MatchError`s from `read()` calls unless you know in advance that your data is correct.

This is going to change in the nearest future; some special kinds of exceptions are going to be introduced,
and safe methods like `readSafely[T](value: backend.BValue): Try[T]` are likely to be added.


Plans
-----

* Add proper exception handling
* Consider adding support for more types
* Consider adding more converters (e.g. for tuples)
* Add more backends (e.g. BSON backend)
* Add more tests
* Add more documentation


Changelog
---------

### 0.1.0 (in progress)

* More serializer instances
* Added generic handling for accurate numbers serialization
* Added collections backend
* Support for recursive types
* Added converters
* Improved API for custom serializers
* Added support for renaming fields and sealed trait variants
* Added support for default values in case classes
* Added test generators
* Started adding tests

### 0.0.2

* Added more instances for primitive types
* Improved API

### 0.0.1

* Initial release
