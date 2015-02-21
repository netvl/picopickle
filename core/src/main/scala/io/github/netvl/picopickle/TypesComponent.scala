package io.github.netvl.picopickle

import scala.annotation.implicitNotFound

/**
 * Contains basic types used by the library: [[TypesComponent#Reader Reader]] and
 * [[TypesComponent#Writer Writer]], and basic constructors for them.
 *
 * Mixed into every [[Pickler]] object.
 */
trait TypesComponent { this: BackendComponent =>
  /**
   * Convenient alias for [[scala.PartialFunction PartialFunction]].
   */
  final type PF[-A, +B] = PartialFunction[A, B]

  /**
   * A type class trait for writing arbitrary values of the specified type to the backend representation.
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
     * @param value a value to be serialized
     * @param acc possibly absent accumulator
     * @return serialized representation of `value`
     */
    def write0(value: T, acc: Option[backend.BValue]): backend.BValue

    /**
     * Wraps [[Writer.write0 write0]] call, passing [[scala.None None]] as the second argument.
     *
     * Just a shorthand for `write0(value, None)`.
     *
     * @param value a value to be serialized
     * @return serialized representation of `value`
     */
    final def write(value: T): backend.BValue = write0(value, None)
  }

  /**
   * Contains various constructors to create custom [[Writer Writers]].
   */
  object Writer {
    /**
     * Creates a new writer from a function of type `T => PF[Option[backend.BValue], backend.BValue]`.
     *
     * Mostly intended for internal use. Regular clients should use [[Writer.apply apply]] method.
     *
     * @param ff a function defining writer behavior
     * @tparam T source type
     * @return a writer delegating to the provided function
     */
    def fromPF0[T](ff: T => PF[Option[backend.BValue], backend.BValue]) =
      new Writer[T] {
        override def write0(value: T, acc: Option[backend.BValue]): backend.BValue =
          ff(value)(acc)
      }

    /**
     * Creates a new writer from a partial function of type `PF[(T, Option[backend.BValue]), backend.BValue]`.
     *
     * Mostly intended for internal use. Regular clients should use [[Writer.apply apply]] method.
     *
     * @param ff a function defining writer behavior
     * @tparam T source type
     * @return a writer delegating to the provided function
     */
    def fromPF1[T](ff: PF[(T, Option[backend.BValue]), backend.BValue]) =
      new Writer[T] {
        override def write0(value: T, acc: Option[backend.BValue]): backend.BValue =
          ff(value -> acc)
      }

    /**
     * Creates a new writer from a partial function of type `PF[T, backend.BValue]`.
     *
     * The main constructor for custom writers. The writers returned by this function ignore
     * the accumulator argument (as most of writers should do).
     *
     * @param ff a function defining writer behavior
     * @tparam T source type
     * @return a writer delegating to the provided function
     */
    def apply[T](ff: PF[T, backend.BValue]) =
      new Writer[T] {
        override def write0(value: T, acc: Option[backend.BValue]): backend.BValue =
          ff(value)
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
    def canRead(value: backend.BValue): Boolean = true
    def read(value: backend.BValue): T

    final def orElse(other: Reader[T]): Reader[T] = new Reader[T] {
      override def canRead(value: backend.BValue) =
        source.canRead(value) || other.canRead(value)
      override def read(value: backend.BValue): T =
        if (source.canRead(value)) source.read(value) else other.read(value)
    }

    final def orElse(other: PF[backend.BValue, T]): Reader[T] = this orElse Reader(other)

    final def andThen[U](f: T => U): Reader[U] = new Reader[U] {
      override def canRead(value: backend.BValue) = source.canRead(value)
      override def read(value: backend.BValue): U = f(source.read(value))
    }
  }

  object Reader {
    def apply[T](f: PF[backend.BValue, T]) =
      new Reader[T] {
        override def canRead(value: backend.BValue) =  f.isDefinedAt(value)
        override def read(value: backend.BValue): T = f(value)
      }
  }

  type ReadWriter[T] = Reader[T] with Writer[T]

  object ReadWriter {
    def reading[T](rf: PF[backend.BValue, T]) = new WriterBuilder(rf)
    def writing[T](wf: PF[T, backend.BValue]) = new ReaderBuilder(wf)
    
    class WriterBuilder[T](rf: PF[backend.BValue, T]) {
      def writing(wf: PF[T, backend.BValue]): ReadWriter[T] = new PfReadWriter[T](rf, wf)
    }

    class ReaderBuilder[T](wf: PF[T, backend.BValue]) {
      def reading(rf: PF[backend.BValue, T]): ReadWriter[T] = new PfReadWriter[T](rf, wf)
    }

    private class PfReadWriter[T](rf: PF[backend.BValue, T], wf: PF[T, backend.BValue]) extends Reader[T] with Writer[T] {
      override def canRead(value: backend.BValue) = rf.isDefinedAt(value)
      override def read(value: backend.BValue) = rf(value)

      override def write0(value: T, acc: Option[backend.BValue]) = wf(value)
    }
  }
}

