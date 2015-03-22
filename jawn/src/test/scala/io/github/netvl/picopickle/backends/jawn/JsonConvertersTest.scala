package io.github.netvl.picopickle.backends.jawn

import io.github.netvl.picopickle.ConvertersTestBase

class JsonConvertersTest extends ConvertersTestBase with JsonPickler {
  override lazy val backendName: String = "JSON"
}
