package io.github.netvl.picopickle.backends.mongodb

import io.github.netvl.picopickle.{TypesComponent, DefaultPickler, ExceptionsComponent, BackendComponent}
import org.bson.types.ObjectId

trait MongodbBsonBackendComponent extends BackendComponent {
  override val backend = MongodbBsonBackend
}

trait MongodbBsonExceptionsComponent extends ExceptionsComponent {
  this: BackendComponent =>

  case class BsonParseException(message: String, cause: Throwable)
    extends BaseException(message, cause)
}

trait MongodbBsonSerializersComponent {
  this: MongodbBsonBackendComponent with TypesComponent =>

  implicit val symbolReadWriter: ReadWriter[Symbol] = ReadWriter.writing(backend.makeSymbol)
    .reading {
      case backend.BsonExtract.Symbol(sym) => sym
    }.orThrowing(whenReading = "symbol", expected = "symbol")

  implicit val binaryReadWriter: ReadWriter[Array[Byte]] = ReadWriter.writing(backend.makeBinary)
    .reading {
      case backend.BsonExtract.Binary(arr) => arr
    }.orThrowing(whenReading = "array of bytes", expected = "binary")

  implicit val intReadWriter: ReadWriter[Int] = ReadWriter.writing(backend.makeInt32)
    .reading {
      case backend.BsonExtract.Int32(n) => n
    }.orThrowing(whenReading = "int", expected = "32-bit integer")

  implicit val longReadWriter: ReadWriter[Long] = ReadWriter.writing(backend.makeInt64)
    .reading {
      case backend.BsonExtract.Int64(n) => n
    }.orThrowing(whenReading = "long", expected = "64-bit integer")

  implicit val doubleReadWriter: ReadWriter[Double] = ReadWriter.writing(backend.makeDouble)
    .reading {
      case backend.BsonExtract.Double(n) => n
    }.orThrowing(whenReading = "double", expected = "double")

  implicit val objectIdReadWriter: ReadWriter[ObjectId] = ReadWriter.writing(backend.makeObjectId)
    .reading {
      case backend.BsonExtract.ObjectId(oid) => oid
    }.orThrowing(whenReading = "object id", expected = "object id")
}

trait MongodbBsonPickler
  extends DefaultPickler
  with MongodbBsonBackendComponent
  with MongodbBsonSerializersComponent
  with MongodbBsonExceptionsComponent

object MongodbBsonPickler extends MongodbBsonPickler
