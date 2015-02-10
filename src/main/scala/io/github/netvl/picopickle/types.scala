package io.github.netvl.picopickle

trait Writer[T] {
  def write0(implicit b: Backend): (T, Option[b.BValue]) => Option[b.BValue]
  final def write(implicit b: Backend): T => Option[b.BValue] = value => write0(b)(value, None)
}

trait Reader[T] {
  def read(implicit b: Backend): b.BValue => T
}