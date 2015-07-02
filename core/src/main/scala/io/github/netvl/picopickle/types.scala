package io.github.netvl.picopickle

import scala.annotation.implicitNotFound

/**
 * Contains basic types used by the library: [[TypesComponent#Reader Reader]] and
 * [[TypesComponent#Writer Writer]], and basic constructors for them.
 *
 * Mixed into every [[Pickler]] object.
 */
trait TypesComponent {
  this: BackendComponent with ExceptionsComponent with NullHandlerComponent =>
  /**
   * Convenient alias for [[scala.PartialFunction PartialFunction]].
   */
  final type PF[-A, +B] = PartialFunction[A, B]

  /**
   * A type class trait for writing objects of the specified type to their backend representation.
   *
   * All serialization is done by implicit instances of this trait.
   *
   * Most clients don't need to subclass this trait directly; use [[Writer$ Writer]] companion
   * object to create writers.
   *
   * Serialization is a success-only process: correctly written serializers will always succeed to write
   * their objects. It is expected that [[Writer]] instances can handle ''all'' values of their source types,
   * that is, writers are like total functions.
   *
   * @tparam T source type
   */
  @implicitNotFound("Don't know how to write ${T}; make sure that an implicit `Writer[${T}]` is in scope")
  trait Writer[T] {
    /**
     * Serializes the given value into its backend representation.
     *
     * This method also accepts an ''accumulator'' parameter which is used
     * when serializing complex objects which require multiple serializers
     * to work together (for example, serializing `HList`s to obtain a map).
     *
     * This method shouldn't be invoked directly as it is for internal use. Use [[write]] method
     * instead.
     *
     * @param value a value to be serialized
     * @param acc possibly absent accumulator
     * @return serialized representation of `value`
     */
    def write0(value: T, acc: Option[backend.BValue]): backend.BValue

    /**
     * Wraps [[Writer.write0 write0]] call, passing [[scala.None None]] as the second argument.
     *
     * Just a shorthand for `write0(value, None)`. This is the main method which should be used
     * for writing objects.
     *
     * @param value a value to be serialized
     * @return serialized representation of `value`
     */
    final def write(value: T): backend.BValue = write0(value, None)
  }

  /**
   * Contains various constructors for custom [[Writer Writers]].
   */
  object Writer {
    /**
     * Creates a new writer from a function of type `T => (Option[backend.BValue] => backend.BValue)`.
     *
     * Mostly intended for internal use. Regular clients should use [[Writer$.apply apply]] method.
     *
     * @param ff a function defining writer behavior
     * @tparam T source type
     * @return a writer delegating to the provided function
     */
    def fromF0[T](ff: T => (Option[backend.BValue] => backend.BValue)): Writer[T] =
      new Writer[T] {
        override def write0(value: T, acc: Option[backend.BValue]): backend.BValue =
          nullHandler.toBackend[T](value, ff(_)(acc))
      }

    /**
     * Same as [[Writer$.writePF0 writePF0]], but does not delegate nulls handling to `NullHandler`.
     *
     * Mostly intended for internal use. Regular clients should use [[Writer$.apply apply]] method.
     *
     * @param ff a function defining writer behavior
     * @tparam T source type
     * @return a writer delegating to the provided function
     */
    def fromF0N[T](ff: T => (Option[backend.BValue] => backend.BValue)): Writer[T] =
      new Writer[T] {
        override def write0(value: T, acc: Option[backend.BValue]): backend.BValue =
          ff(value)(acc)
      }

    /**
     * Creates a new writer from a partial function of type `(T, Option[backend.BValue]) => backend.BValue`.
     *
     * Mostly intended for internal use. Regular clients should use [[Writer$.apply apply]] method.
     *
     * @param ff a function defining writer behavior
     * @tparam T source type
     * @return a writer delegating to the provided function
     */
    def fromF1[T](ff: (T, Option[backend.BValue]) => backend.BValue): Writer[T] =
      new Writer[T] {
        override def write0(value: T, acc: Option[backend.BValue]): backend.BValue =
          nullHandler.toBackend[T](value, ff(_, acc))
      }

    /**
     * Creates a new writer from a partial function of type `T => backend.BValue`.
     *
     * This is the main constructor for custom writers. The writers returned by this function ignore
     * the accumulator argument (as most of writers should do).
     *
     * An example:
     * {{{
     *   case class A(x: Int, y: String)
     *
     *   import backendConversionImplicits._
     *   implicit val aWriter: Writer[A] = Writer {
     *     case A(x, y) => Map("a" -> x.toBackend, "b" -> y.toBackend).toBackend
     *   }
     * }}}
     *
     * As manual construction of complex objects may quickly turn very unwieldy, it is recommended
     * to use [[io.github.netvl.picopickle.ConvertersComponent converters]] instead.
     *
     * @param ff a function defining writer behavior
     * @tparam T source type
     * @return a writer delegating to the provided function
     */
    def apply[T](ff: T => backend.BValue): Writer[T] =
      new Writer[T] {
        override def write0(value: T, acc: Option[backend.BValue]): backend.BValue =
          nullHandler.toBackend[T](value, ff)
      }
  }

  /**
   * A type class for reading a backend representation into an object of the specified type.
   *
   * All deserialization is done by implicit instances of this trait.
   *
   * Most clients don't need to subclass this trait directly; use [[Reader$ Reader]] companion object
   * to create readers.
   *
   * Deserialization process can fail if its input is not valid. Consequently, readers are more like
   * partial functions: they can fail on certain inputs.
   *
   * @tparam T target type
   */
  @implicitNotFound("Don't know how to read ${T}; make sure that an implicit `Reader[${T}]` is in scope")
  trait Reader[T] { source =>
    /**
     * Checks if this reader can handle the provided value.
     *
     * @param value a backend value
     * @return `true` if this reader can read from `value`, `false` otherwise
     */
    def canRead(value: backend.BValue): Boolean

    /**
     * Deserializes the value of the specified type from the provided backend value.
     *
     * This method should fail with an exception if [[Reader.canRead canRead]] returns `false`
     * on this value and it returns a deserialized object successfully otherwise.
     *
     * @param value a backend value
     * @return deserialized variant of `value`
     */
    def read(value: backend.BValue): T

    /**
     * Deserializes the value of the specified type from the provided backend value or applies the given
     * function if it is impossible.
     *
     * This method is equivalent to `if (this.canRead(value)) this.read(value) else fallback(value)`
     * (which is in fact its default implementation) but it can be overridden to avoid excessive
     * checks. See [[PartialFunction.applyOrElse]] method for longer explanation.
     *
     * [[Reader.apply]] and [[Reader.reading]] method overrides this method to employ the provided partial function
     * `applyOrElse` method. Consider implementing this method for your readers if for some
     * reason you don't use `Reader.apply` or `Reader.reading`.
     *
     * @param value a backend value
     * @param fallback a fallback function
     * @return deserialized variant of `value` or the result of `fallback` application
     */
    def readOrElse(value: backend.BValue, fallback: backend.BValue => T): T =
      if (this.canRead(value)) this.read(value) else fallback(value)

    /**
     * Combines this reader with the specified fallback reader which is used if this reader can't
     * handle the provided value based on its [[Reader.canRead canRead]] result.
     *
     * @param other the fallback reader
     * @return a reader which delegates to this reader if this reader can deserialize a value
     *         or to the `other` reader otherwise
     */
    final def orElse(other: Reader[T]): Reader[T] = new Reader[T] {
      override def canRead(value: backend.BValue) =
        source.canRead(value) || other.canRead(value)
      override def read(value: backend.BValue): T =
        source.readOrElse(value, other.read)
    }

    /**
     * A shorthand for `this.orElse(Reader(other))`, where `other` is a partial function.
     * See [[Reader.apply]].
     *
     * @param other a partial function which is used to create the fallback reader
     * @return see another `orElse` method
     */
    final def orElse(other: PF[backend.BValue, T]): Reader[T] = this orElse Reader(other)

    /**
     * Returns a reader which applies the given function to the result of the deserialization.
     *
     * @param f a transformation function
     * @tparam U result type
     * @return a reader which reads a value of type `T` and then applies `f` to obtain a value of type `U`
     */
    final def andThen[U](f: T => U): Reader[U] = new Reader[U] {
      override def canRead(value: backend.BValue) = source.canRead(value)
      override def read(value: backend.BValue): U = f(source.read(value))
    }
  }

  /**
   * Contains various constructors for custom [[Reader Readers]].
   */
  object Reader {
    /**
     * Creates a reader using the given partial function.
     *
     * This is the main constructor for custom readers. The provided partial function is used
     * to reconstruct a value from its backend representation. [[Reader.canRead canRead]] method
     * of the constructed reader delegates to [[scala.PartialFunction.isDefinedAt isDefinedAt]] on
     * the partial function.
     *
     * An example:
     * {{{
     *   case class A(x: Int, y: String)
     *
     *   implicit val aReader: Reader[A] = Reader {
     *     case backend.Extract.Object(m) if m.contains("a") && m.contains("b") &&
     *                                       backend.getNumber(m("a")).isDefined &&
     *                                       backend.getString(m("b")).isDefined =>
     *       A(
     *         backend.Extract.Number.unapply(m("a")).get,
     *         backend.Extract.String.unapply(m("b")).get
     *       )
     *   }
     * }}}
     *
     * As manual deconstruction of complex object may quickly turn very unwieldy, it is recommended
     * to use [[io.github.netvl.picopickle.ConvertersComponent converters]] instead.
     *
     * @param f a partial function from backend representation to the target type
     * @tparam T target type
     * @return a reader delegating to the provided function.
     */
    def apply[T](f: PF[backend.BValue, T]): Reader[T] =
      new Reader[T] {
        override def canRead(value: backend.BValue) = nullHandler.canRead(value, f.isDefinedAt)

        override def read(value: backend.BValue): T =
          readOrElse(value, defaultReadError)

        override def readOrElse(value: backend.BValue, fallback: backend.BValue => T): T =
          nullHandler.fromBackend(value, v => f.applyOrElse(v, fallback))
      }

    def reading[T](f: PF[backend.BValue, T]): ReaderBuilder[T] = new ReaderBuilder[T](f)

    class ReaderBuilder[T](f: PF[backend.BValue, T]) {
      def orThrowing(fmt: backend.BValue => String): Reader[T] = Reader(f orElse {
        case value => customReadError(fmt)(value)
      })
      def orThrowing(whenReading: => String, expected: => String): Reader[T] = Reader(f orElse {
        case value => parameterizedReadError(whenReading, expected)(value)
      })
    }
  }

  type ReadWriter[T] = Reader[T] with Writer[T]

  object ReadWriter {
    def apply[T](implicit r: Reader[T], w: Writer[T]): ReadWriter[T] = new Reader[T] with Writer[T] {
      override def canRead(value: backend.BValue) = r.canRead(value)
      override def read(value: backend.BValue) = r.read(value)
      override def readOrElse(value: backend.BValue, fallback: backend.BValue => T) = r.readOrElse(value, fallback)
      override def write0(value: T, acc: Option[backend.BValue]) = w.write0(value, acc)
    }

    def reading[T](rf: PF[backend.BValue, T]) = new WriterBuilder(rf, defaultReadError)
    def writing[T](wf: T => backend.BValue) = new ReaderBuilder(wf)
    
    class WriterBuilder[T](rf: PF[backend.BValue, T], error: backend.BValue => Nothing) {
      def writing(wf: T => backend.BValue) = new PfReadWriter[T](rf, wf, error)
      def orThrowing(whenReading: => String, expected: => String) = new WriterBuilder[T](rf, parameterizedReadError(whenReading, expected))
      def orThrowing(fmt: backend.BValue => String) = new WriterBuilder[T](rf, customReadError(fmt))
    }

    class ReaderBuilder[T](wf: T => backend.BValue) {
      def reading(rf: PF[backend.BValue, T]) = new PfReadWriter[T](rf, wf, defaultReadError)
    }

    class PfReadWriter[T] private[ReadWriter] (rf: PF[backend.BValue, T],
                                               wf: T => backend.BValue,
                                               error: backend.BValue => Nothing) extends Reader[T] with Writer[T] {
      def orThrowing(whenReading: => String, expected: => String): ReadWriter[T] =
        new PfReadWriter[T](rf, wf, parameterizedReadError(whenReading, expected))
      def orThrowing(fmt: backend.BValue => String): ReadWriter[T] =
        new PfReadWriter[T](rf, wf, customReadError(fmt))

      override def canRead(value: backend.BValue) = nullHandler.canRead(value, rf.isDefinedAt)

      override def read(value: backend.BValue) =
        readOrElse(value, error)

      override def readOrElse(value: backend.BValue, fallback: backend.BValue => T) =
        nullHandler.fromBackend(value, v => rf.applyOrElse(v, fallback))

      override def write0(value: T, acc: Option[backend.BValue]) = nullHandler.toBackend(value, wf)
    }
  }

  private def defaultReadError(v: backend.BValue): Nothing =
    throw ReadException(s"unexpected backend value: $v", data = v)
  private def parameterizedReadError(reading: => String, expected: => String)(value: backend.BValue): Nothing =
    throw ReadException(reading, expected, value)
  private def customReadError(fmt: backend.BValue => String)(value: backend.BValue): Nothing =
    throw ReadException(fmt(value), data = value)
}

