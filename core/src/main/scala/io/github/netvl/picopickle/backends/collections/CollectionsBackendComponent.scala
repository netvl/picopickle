package io.github.netvl.picopickle.backends.collections

import io.github.netvl.picopickle.BackendComponent

trait CollectionsBackendComponent extends BackendComponent {
  override val backend = CollectionsBackend
}
