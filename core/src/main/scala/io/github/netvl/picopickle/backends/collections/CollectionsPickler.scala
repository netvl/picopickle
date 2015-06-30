package io.github.netvl.picopickle.backends.collections

import io.github.netvl.picopickle.DefaultPickler

object CollectionsPickler extends CollectionsPickler

trait CollectionsPickler extends DefaultPickler with CollectionsBackendComponent {
  override implicit val charWriter: Writer[Char] = Writer[Char](identity)
  override implicit val charReader: Reader[Char] = Reader.reading {
    case c: Char => c
  }.orThrowing(whenReading = "character", expected = "character")
}