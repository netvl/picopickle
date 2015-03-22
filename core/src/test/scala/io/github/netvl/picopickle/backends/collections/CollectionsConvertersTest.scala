package io.github.netvl.picopickle.backends.collections

import io.github.netvl.picopickle.ConvertersTestBase

class CollectionsConvertersTest extends ConvertersTestBase with CollectionsPickler {
  override lazy val backendName: String = "collections"
}
