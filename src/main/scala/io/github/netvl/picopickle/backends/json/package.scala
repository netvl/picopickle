package io.github.netvl.picopickle.backends

import io.github.netvl.picopickle.{JsonAst, Backend}

package object json {
  implicit val jsonBackend: Backend = JsonAst.Backend
}
