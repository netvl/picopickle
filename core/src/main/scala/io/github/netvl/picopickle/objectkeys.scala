package io.github.netvl.picopickle

import scala.annotation.implicitNotFound
import scala.{collection => coll}
import scala.collection.{mutable => mut, immutable => imm}

trait ObjectKeyTypesComponent {
  @implicitNotFound("Don't know how to write ${T} as a map key; make sure that an implicit `ObjectKeyWriter[${T}]` is in scope")
  trait ObjectKeyWriter[T] {
    def write(value: T): String
  }

  object ObjectKeyWriter {
    def apply[T](conv: T => String): ObjectKeyWriter[T] = new ObjectKeyWriter[T] {
      override def write(value: T): String = conv(value)
    }
  }

  @implicitNotFound("Don't know how to read ${T} as a map key; make sure that an implicit `ObjectKeyReader[${T}]` is in scope")
  trait ObjectKeyReader[T] {
    def read(value: String): T
  }

  object ObjectKeyReader {
    def apply[T](conv: String => T): ObjectKeyReader[T] = new ObjectKeyReader[T] {
      override def read(value: String): T = conv(value)
    }
  }

  type ObjectKeyReadWriter[T] = ObjectKeyReader[T] with ObjectKeyWriter[T]

  object ObjectKeyReadWriter {
    def apply[T](from: String => T): ObjectKeyReadWriter[T] = apply(_.toString, from)

    def apply[T](to: T => String, from: String => T): ObjectKeyReadWriter[T] = new ObjectKeyReader[T] with ObjectKeyWriter[T] {
      override def write(value: T): String = to(value)
      override def read(value: String): T = from(value)
    }

    def apply[T](implicit r: ObjectKeyReader[T], w: ObjectKeyWriter[T]) = new ObjectKeyReader[T] with ObjectKeyWriter[T] {
      override def write(value: T): String = w.write(value)
      override def read(value: String): T = r.read(value)
    }
  }

}

trait ObjectKeyWritersComponent {
  this: ObjectKeyTypesComponent =>

  implicit val stringObjectKeyWriter: ObjectKeyWriter[String] = ObjectKeyWriter(identity)
}

trait ObjectKeyReadersComponent {
  this: ObjectKeyTypesComponent =>

  implicit val stringObjectKeyReader: ObjectKeyReader[String] = ObjectKeyReader(identity)
}

trait ObjectKeyReaderWritersComponent extends ObjectKeyReadersComponent with ObjectKeyWritersComponent {
  this: ObjectKeyTypesComponent =>
}
