package io.github.netvl.picopickle.backends.jawn

import io.github.netvl.picopickle.BackendComponent

trait JsonBackendComponent extends BackendComponent {
  override val backend = JsonAst.Backend
}
