package io.github.netvl.picopickle.backends.json

import io.github.netvl.picopickle.BackendComponent

trait JsonBackendComponent extends BackendComponent {
  override val backend = JsonAst.Backend
}
