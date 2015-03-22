picopickle 0.0.2
================

(This readme is currently in flux and may reflect features unavailable in the latest released version.
This situation is only temporary)

picopickle is a serialization library for Scala. Its main features are:

* Small and almost dependency-less (the core library depends only on [shapeless]).
* Extensibility: you can define your own serializators for your types and you can create
  custom *backends*, that is, you can use the same library for the different serialization formats
  (collections, JSON, BSON, etc.).
* Static serialization without reflection: shapeless [`Generic`][Generic] macros are used to
  provide serializers for arbitrary types.

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
is represented by a `Coproduct` of the corresponding `HList`s.

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

That is, each backend should provide methods to convert from `BValue` to `Map[String, BValue]` and back etc.

#### Instantiating custom serializers

picopickle defines `Writer` and `Reader` basic types in `TypesComponent` which are responsible
for converting arbitrary types to their backend representation and back, respectively. The main
way to construct custom serializers is to use `apply` method on `Reader` and `Writer` companion objects,
which take `PartialFunction[backend.BValue, T]` and `PartialFunction[T, backend.BValue]`, respectively
(you can find an example of both above).

Despite that `Writer` takes a partial function, it still should be able to serialize any values
of their corresponding type. `Reader`, however, can fail to match the backend representation. Currently
it will lead to a `MatchError` thrown by `read()` call; this is going to improve in future.

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

`Backend` trait provides methods to create and deconstruct objects of backend representation. You can find
more on them in the section on backends below. To simplify writing custom serializers, however, picopickle
provides a set of tools which help you writing conversions. The most basic of them are extractors and
and backend conversion implicits.

Backend object contains several singleton objects with `unapply` methods which can be used to pattern-match
on `backend.BValue` and obtain the low-level values out of it, for example, obtain a `Map[String, backend.BValue]`
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

These extractors


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

Additionally you can customize the discriminator key used by shapeless serializers by overriding
`discriminatorKey` field defined in `io.github.netvl.picopickle.SealedTraitDiscriminator` trait
(its default value is `"$variant"`):

```scala
import io.github.netvl.picopickle.backends.jawn.JsonPickler

object CustomPickler extends JsonPickler {
  override val discriminatorKey = "$type"
}
```

(of course, you can extract it into a separate trait and mix it into different picklers)

### Collections pickler

**TODO**

### JSON pickler

**TODO**

Converters
----------

**TODO**

Plans
-----

* Improve custom serializers writing ergonomics
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
