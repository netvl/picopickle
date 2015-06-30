package io.github.netvl.picopickle.backends.mongodb

import io.github.netvl.picopickle.ConvertersTestBase

class MongodbBsonConvertersTest extends ConvertersTestBase with MongodbBsonPickler {
  override lazy val backendName: String = "MongoDB BSON"
}
