package io.github.netvl.picopickle.backends.jawn

import io.github.netvl.picopickle.{TypesComponent, DefaultPickler}

trait JsonPickler extends DefaultPickler with TypesComponent with JsonBackendComponent
object JsonPickler extends JsonPickler
