package io.github.netvl.picopickle

import scala.annotation.implicitNotFound
import scala.reflect.ClassTag
import scala.{collection => coll}
import scala.collection.{mutable => mut}
import scala.collection.{immutable => imm}
import scala.collection.generic.CanBuildFrom
import scala.language.higherKinds
import scala.language.experimental.macros

trait MapPicklingComponent {
  @implicitNotFound("Can't [un]pickle a map with keys of type ${T} neither as an object nor as an array of tuples; " +
    "either define an `ObjectKeyReadWriter[${T}]` converter or explicitly allow " +
    "serializing this map as an array of tuples via putting " +
    "an implicit value returned by `allowMapPicklingWithKeyOfType[${T}]` in scope")
  sealed class MapPicklingIsAllowed[T]
  protected object MapPicklingIsAllowed extends MapPicklingIsAllowed[Nothing]
  def allowMapPicklingWithKeysOfType[T]: MapPicklingIsAllowed[T] =
    MapPicklingIsAllowed.asInstanceOf[MapPicklingIsAllowed[T]]
  
  def mapPicklingIsAllowedByDefault[T]: MapPicklingIsAllowed[T]
}

trait MapPicklingEnabledByDefault extends MapPicklingComponent {
  // by default it is enabled for all key types
  override implicit def mapPicklingIsAllowedByDefault[T]: MapPicklingIsAllowed[T] =
    allowMapPicklingWithKeysOfType[T]
}

trait MapPicklingDisabledByDefault extends MapPicklingComponent {
  this: ObjectKeyTypesComponent =>

  // here it is disabled via an aborting macro
  override implicit def mapPicklingIsAllowedByDefault[T]: MapPicklingIsAllowed[T] = macro MapPicklingDisabledByDefaultMacros.killItself[T]

  // but another implicit value is defined for all keys which are readable/writable as object keys
  implicit def mapPicklingIsAllowedForAppropriateKeyTypes[T: ObjectKeyReader: ObjectKeyWriter] =
    allowMapPicklingWithKeysOfType[T]
}

object MapPicklingDisabledByDefaultMacros {
  import BinaryVersionSpecificDefinitions._

  def killItself[T: c.WeakTypeTag](c: Context): c.Expr[T] =
    c.abort(c.enclosingPosition, "aborting expansion of an offending implicit")
}

trait CollectionWriters {
  this: ObjectKeyTypesComponent with MapPicklingComponent with BackendComponent with TypesComponent =>

  protected final def mkIterableWriter[T, C[_] <: Iterable[_]](implicit w: Writer[T]): Writer[C[T]] =
    Writer.fromPF0 { c => {
      case None => backend.makeArray(
        c.iterator.asInstanceOf[Iterator[T]].map(e => w.write(e)).toVector
      )
    }}

  protected final def mkMapWriter[A, B, M[K, V] <: coll.Map[K, V] with coll.MapLike[K, V, M[K, V]]]
      (implicit wa: Writer[A], wb: Writer[B], wab: Writer[(A, B)], kw: ObjectKeyWriter[A]): Writer[M[A, B]] =
    if (kw != null) Writer.fromPF0[M[A, B]] { (m: coll.MapLike[A, B, M[A, B]]) => {
      case Some(backend.Get.Object(obj)) => m.foldLeft(obj) { (acc, t) =>
        backend.setObjectKey(acc, kw.write(t._1), wb.write(t._2))
      }
      case None => backend.makeObject(m.map { case (k, v) => (kw.write(k), wb.write(v)) }.toMap)
    }}
    else Writer.fromPF0[M[A, B]] { (m: coll.MapLike[A, B, M[A, B]]) => {
      case None =>
        backend.makeArray(m.map(t => wab.write(t)).toVector)
    }}

  implicit def iterableWriter[T: Writer]: Writer[Iterable[T]] = mkIterableWriter[T, Iterable]

  implicit def seqWriter[T: Writer]: Writer[coll.Seq[T]] = mkIterableWriter[T, coll.Seq]
  implicit def immSeqWriter[T: Writer]: Writer[imm.Seq[T]] = mkIterableWriter[T, imm.Seq]
  implicit def mutSeqWriter[T: Writer]: Writer[mut.Seq[T]] = mkIterableWriter[T, mut.Seq]

  implicit def setWriter[T: Writer]: Writer[coll.Set[T]] = mkIterableWriter[T, coll.Set]
  implicit def immSetWriter[T: Writer]: Writer[imm.Set[T]] = mkIterableWriter[T, imm.Set]
  implicit def mutSetWriter[T: Writer]: Writer[mut.Set[T]] = mkIterableWriter[T, mut.Set]

  implicit def indexedSeqWriter[T: Writer]: Writer[coll.IndexedSeq[T]] = mkIterableWriter[T, coll.IndexedSeq]
  implicit def immIndexedSeqWriter[T: Writer]: Writer[imm.IndexedSeq[T]] = mkIterableWriter[T, imm.IndexedSeq]
  implicit def mutIndexedSeqWriter[T: Writer]: Writer[mut.IndexedSeq[T]] = mkIterableWriter[T, mut.IndexedSeq]

  implicit def linearSeqWriter[T: Writer]: Writer[coll.LinearSeq[T]] = mkIterableWriter[T, coll.LinearSeq]
  implicit def immLinearSeqWriter[T: Writer]: Writer[imm.LinearSeq[T]] = mkIterableWriter[T, imm.LinearSeq]
  implicit def mutLinearSeqWriter[T: Writer]: Writer[mut.LinearSeq[T]] = mkIterableWriter[T, mut.LinearSeq]

  implicit def sortedSetWriter[T: Writer: Ordering]: Writer[coll.SortedSet[T]] = mkIterableWriter[T, coll.SortedSet]
  implicit def immSortedSetWriter[T: Writer: Ordering]: Writer[imm.SortedSet[T]] = mkIterableWriter[T, imm.SortedSet]
  implicit def mutSortedSetWriter[T: Writer: Ordering]: Writer[mut.SortedSet[T]] = mkIterableWriter[T, mut.SortedSet]

  implicit def queueWriter[T: Writer]: Writer[imm.Queue[T]] = mkIterableWriter[T, imm.Queue]
  implicit def vectorWriter[T: Writer]: Writer[imm.Vector[T]] = mkIterableWriter[T, imm.Vector]
  implicit def listWriter[T: Writer]: Writer[imm.List[T]] = mkIterableWriter[T, imm.List]
  implicit def streamWriter[T: Writer]: Writer[imm.Stream[T]] = mkIterableWriter[T, imm.Stream]
  implicit def listSetWriter[T: Writer]: Writer[imm.ListSet[T]] = mkIterableWriter[T, imm.ListSet]
  implicit def treeSetWriter[T: Writer: Ordering]: Writer[imm.TreeSet[T]] = mkIterableWriter[T, imm.TreeSet]

  implicit def immHashSetWriter[T: Writer]: Writer[imm.HashSet[T]] = mkIterableWriter[T, imm.HashSet]
  implicit def mutHashSetWriter[T: Writer]: Writer[mut.HashSet[T]] = mkIterableWriter[T, mut.HashSet]

  implicit def bufferWriter[T: Writer]: Writer[mut.Buffer[T]] = mkIterableWriter[T, mut.Buffer]
  implicit def arrayBufferWriter[T: Writer]: Writer[mut.ArrayBuffer[T]] = mkIterableWriter[T, mut.ArrayBuffer]
  implicit def linkedListWriter[T: Writer]: Writer[mut.LinkedList[T]] = mkIterableWriter[T, mut.LinkedList]
  implicit def linkedHashSetWriter[T: Writer]: Writer[mut.LinkedHashSet[T]] = mkIterableWriter[T, mut.LinkedHashSet]

  implicit def mapWriter[A: Writer, B: Writer](implicit allowed: MapPicklingIsAllowed[A], 
                                               kw: ObjectKeyWriter[A] = null, wab: Writer[(A, B)]): Writer[coll.Map[A, B]] = mkMapWriter[A, B, coll.Map]
  implicit def immMapWriter[A: Writer, B: Writer](implicit allowed: MapPicklingIsAllowed[A], 
                                                  kw: ObjectKeyWriter[A] = null, wab: Writer[(A, B)]): Writer[imm.Map[A, B]] = mkMapWriter[A, B, imm.Map]
  implicit def mutMapWriter[A: Writer, B: Writer](implicit allowed: MapPicklingIsAllowed[A], 
                                               kw: ObjectKeyWriter[A] = null, wab: Writer[(A, B)]): Writer[mut.Map[A, B]] = mkMapWriter[A, B, mut.Map]

  implicit def immHashMapWriter[A: Writer, B: Writer](implicit allowed: MapPicklingIsAllowed[A], 
                                                      kw: ObjectKeyWriter[A] = null, wab: Writer[(A, B)]): Writer[imm.HashMap[A, B]] = mkMapWriter[A, B, imm.HashMap]
  implicit def mutHashMapWriter[A: Writer, B: Writer](implicit allowed: MapPicklingIsAllowed[A], 
                                                      kw: ObjectKeyWriter[A] = null, wab: Writer[(A, B)]): Writer[mut.HashMap[A, B]] = mkMapWriter[A, B, mut.HashMap]

  implicit def treeMapWriter[A: Writer: Ordering, B: Writer](implicit allowed: MapPicklingIsAllowed[A], 
                                                             kw: ObjectKeyWriter[A] = null, wab: Writer[(A, B)]): Writer[imm.TreeMap[A, B]] = mkMapWriter[A, B, imm.TreeMap]
  implicit def listMapWriter[A: Writer, B: Writer](implicit allowed: MapPicklingIsAllowed[A], 
                                                   kw: ObjectKeyWriter[A] = null, wab: Writer[(A, B)]): Writer[imm.ListMap[A, B]] = mkMapWriter[A, B, imm.ListMap]

  implicit def linkedHashMapWriter[A: Writer, B: Writer](implicit allowed: MapPicklingIsAllowed[A], 
                                                         kw: ObjectKeyWriter[A] = null, wab: Writer[(A, B)]): Writer[mut.LinkedHashMap[A, B]] = mkMapWriter[A, B, mut.LinkedHashMap]

  implicit def arrayWriter[T: Writer]: Writer[Array[T]] = Writer {
    case arr => iterableWriter[T].write(arr)
  }
}

trait CollectionReaders {
  this: ObjectKeyTypesComponent with MapPicklingComponent with BackendComponent with TypesComponent =>

  protected final def mkIterableReader[T, C[_] <: Iterable[_]](implicit r: Reader[T],
                                                               cbf: CanBuildFrom[C[T], T, C[T]]): Reader[C[T]] =
    Reader {
      case backend.Extract.Array(arr) => arr.map(r.read).to[C]
    }

  protected final def mkMapReader[A, B, M[_, _] <: coll.Map[_, _]]
      (implicit ra: Reader[A], rb: Reader[B], kr: ObjectKeyReader[A], rab: Reader[(A, B)],
       cbf: CanBuildFrom[M[A, B], (A, B), M[A, B]]) =
    if (kr != null) Reader {
      case backend.Extract.Object(m) =>
        val builder = cbf.apply()
        m.foreach {
          case (k, v) => builder += (kr.read(k) -> rb.read(v))
        }
        builder.result()
    } else Reader {
      case backend.Extract.Array(arr) =>
        val builder = cbf.apply()
        arr.foreach { e => builder += rab.read(e) }
        builder.result()
    }
  
  implicit def seqReader[T: Reader]: Reader[coll.Seq[T]] = mkIterableReader[T, coll.Seq]
  implicit def immSeqReader[T: Reader]: Reader[imm.Seq[T]] = mkIterableReader[T, imm.Seq]
  implicit def mutSeqReader[T: Reader]: Reader[mut.Seq[T]] = mkIterableReader[T, mut.Seq]
  
  implicit def setReader[T: Reader]: Reader[coll.Set[T]] = mkIterableReader[T, coll.Set]
  implicit def immSetReader[T: Reader]: Reader[imm.Set[T]] = mkIterableReader[T, imm.Set]
  implicit def mutSetReader[T: Reader]: Reader[mut.Set[T]] = mkIterableReader[T, mut.Set]
  
  implicit def indexedSeqReader[T: Reader]: Reader[coll.IndexedSeq[T]] = mkIterableReader[T, coll.IndexedSeq]
  implicit def immIndexedSeqReader[T: Reader]: Reader[imm.IndexedSeq[T]] = mkIterableReader[T, imm.IndexedSeq]
  implicit def mutIndexedSeqReader[T: Reader]: Reader[mut.IndexedSeq[T]] = mkIterableReader[T, mut.IndexedSeq]
  
  implicit def linearSeqReader[T: Reader]: Reader[coll.LinearSeq[T]] = mkIterableReader[T, coll.LinearSeq]
  implicit def immLinearSeqReader[T: Reader]: Reader[imm.LinearSeq[T]] = mkIterableReader[T, imm.LinearSeq]
  implicit def mutLinearSeqReader[T: Reader]: Reader[mut.LinearSeq[T]] = mkIterableReader[T, mut.LinearSeq]
  
  implicit def sortedSetReader[T: Reader: Ordering]: Reader[coll.SortedSet[T]] = mkIterableReader[T, coll.SortedSet]
  implicit def immSortedSetReader[T: Reader: Ordering]: Reader[imm.SortedSet[T]] = mkIterableReader[T, imm.SortedSet]
  implicit def mutSortedSetReader[T: Reader: Ordering]: Reader[mut.SortedSet[T]] = mkIterableReader[T, mut.SortedSet]
  
  implicit def queueReader[T: Reader]: Reader[imm.Queue[T]] = mkIterableReader[T, imm.Queue]
  implicit def vectorReader[T: Reader]: Reader[imm.Vector[T]] = mkIterableReader[T, imm.Vector]
  implicit def listReader[T: Reader]: Reader[imm.List[T]] = mkIterableReader[T, imm.List]
  implicit def streamReader[T: Reader]: Reader[imm.Stream[T]] = mkIterableReader[T, imm.Stream]
  implicit def listSetReader[T: Reader]: Reader[imm.ListSet[T]] = mkIterableReader[T, imm.ListSet]
  implicit def treeSetReader[T: Reader: Ordering]: Reader[imm.TreeSet[T]] = mkIterableReader[T, imm.TreeSet]

  implicit def immHashSetReader[T: Reader]: Reader[imm.HashSet[T]] = mkIterableReader[T, imm.HashSet]
  implicit def mutHashSetReader[T: Reader]: Reader[mut.HashSet[T]] = mkIterableReader[T, mut.HashSet]
  
  implicit def bufferReader[T: Reader]: Reader[mut.Buffer[T]] = mkIterableReader[T, mut.Buffer]
  implicit def arrayBufferReader[T: Reader]: Reader[mut.ArrayBuffer[T]] = mkIterableReader[T, mut.ArrayBuffer]
  implicit def linkedListReader[T: Reader]: Reader[mut.LinkedList[T]] = mkIterableReader[T, mut.LinkedList]
  implicit def linkedHashSetReader[T: Reader]: Reader[mut.LinkedHashSet[T]] = mkIterableReader[T, mut.LinkedHashSet]

  implicit def mapReader[A: Reader, B: Reader](implicit allowed: MapPicklingIsAllowed[A],
                                               kr: ObjectKeyReader[A] = null, rab: Reader[(A, B)]): Reader[coll.Map[A, B]] = mkMapReader[A, B, coll.Map]
  implicit def immMapReader[A: Reader, B: Reader](implicit allowed: MapPicklingIsAllowed[A],
                                                  kr: ObjectKeyReader[A] = null, rab: Reader[(A, B)]): Reader[imm.Map[A, B]] = mkMapReader[A, B, imm.Map]
  implicit def mutMapReader[A: Reader, B: Reader](implicit allowed: MapPicklingIsAllowed[A],
                                                  kr: ObjectKeyReader[A] = null, rab: Reader[(A, B)]): Reader[mut.Map[A, B]] = mkMapReader[A, B, mut.Map]

  implicit def immHashMapReader[A: Reader, B: Reader](implicit allowed: MapPicklingIsAllowed[A],
                                                      kr: ObjectKeyReader[A] = null, rab: Reader[(A, B)]): Reader[imm.HashMap[A, B]] = mkMapReader[A, B, imm.HashMap]
  implicit def mutHashMapReader[A: Reader, B: Reader](implicit allowed: MapPicklingIsAllowed[A],
                                                      kr: ObjectKeyReader[A] = null, rab: Reader[(A, B)]): Reader[mut.HashMap[A, B]] = mkMapReader[A, B, mut.HashMap]

  implicit def treeMapReader[A: Reader: Ordering, B: Reader](implicit allowed: MapPicklingIsAllowed[A],
                                                             kr: ObjectKeyReader[A] = null, rab: Reader[(A, B)]): Reader[imm.TreeMap[A, B]] = mkMapReader[A, B, imm.TreeMap]
  implicit def listMapReader[A: Reader, B: Reader](implicit allowed: MapPicklingIsAllowed[A],
                                                   kr: ObjectKeyReader[A] = null, rab: Reader[(A, B)]): Reader[imm.ListMap[A, B]] = mkMapReader[A, B, imm.ListMap]

  implicit def linkedHashMapReader[A: Reader, B: Reader](implicit allowed: MapPicklingIsAllowed[A],
                                                         kr: ObjectKeyReader[A] = null, rab: Reader[(A, B)]): Reader[mut.LinkedHashMap[A, B]] = mkMapReader[A, B, mut.LinkedHashMap]

  implicit def arrayReader[T: ClassTag](implicit r: Reader[T]): Reader[Array[T]] = Reader {
    case backend.Extract.Array(arr) => arr.map(r.read).toArray[T]
  }
}

trait CollectionReaderWritersComponent extends CollectionReaders with CollectionWriters {
  this: ObjectKeyTypesComponent with MapPicklingComponent with BackendComponent with TypesComponent =>
}
