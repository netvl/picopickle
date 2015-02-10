package com.github.netvl.picopickle

trait PrimitiveWriters {
  implicit val intWriter: Writer[Int] = new Writer[Int] {
    override def write0(implicit b: Backend): (Int, Option[b.BValue]) => Option[b.BValue] = (f, v) => (f, v) match {
      case (n, None) => Some(b.makeNumber(n))
    }
  }

  implicit val stringWriter: Writer[String] = new Writer[String] {
    override def write0(implicit b: Backend): (String, Option[b.BValue]) => Option[b.BValue] = (f, v) => (f, v) match {
      case (s, None) => Some(b.makeString(s))
    }
  }
}

trait PrimitiveReaders {
  implicit val intReader: Reader[Int] = new Reader[Int] {
    override def read(implicit b: Backend): (b.BValue) => Int = {
      case b.Extractors.Number(n) => b.fromNumber(n).intValue()
    }
  }

  implicit val stringReader: Reader[String] = new Reader[String] {
    override def read(implicit b: Backend): (b.BValue) => String = {
      case b.Extractors.String(s) => b.fromString(s)
    }
  }
}

trait PrimitiveReaderWriters extends PrimitiveReaders with PrimitiveWriters
