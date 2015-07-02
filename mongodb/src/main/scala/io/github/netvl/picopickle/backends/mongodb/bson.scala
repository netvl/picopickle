package io.github.netvl.picopickle.backends.mongodb

import java.util.Date

import _root_.io.github.netvl.picopickle.{TypesComponent, DefaultPickler, ExceptionsComponent, BackendComponent}
import org.bson._
import org.bson.types.ObjectId

import scala.reflect.{ClassTag, classTag}

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

  private def identityBsonReadWriter[T <: backend.BValue : ClassTag] =
    ReadWriter.writing[T](identity).reading { case value: T => value }
      .orThrowing(whenReading = classTag[T].runtimeClass.getSimpleName, expected = classTag[T].runtimeClass.getSimpleName)

  implicit val bsonValueReadWriter: ReadWriter[BsonValue] =
    ReadWriter.writing[BsonValue](identity).reading(PartialFunction(identity))

  implicit val bsonDocumentReadWriter: ReadWriter[BsonDocument] = identityBsonReadWriter[BsonDocument]
  implicit val bsonArrayReadWriter: ReadWriter[BsonArray] = identityBsonReadWriter[BsonArray]
  implicit val bsonStringReadWriter: ReadWriter[BsonString] = identityBsonReadWriter[BsonString]
  implicit val bsonNumberReadWriter: ReadWriter[BsonNumber] = identityBsonReadWriter[BsonNumber]
  implicit val bsonBooleanReadWriter: ReadWriter[BsonBoolean] = identityBsonReadWriter[BsonBoolean]
  implicit val bsonNullReadWriter: ReadWriter[BsonNull] = identityBsonReadWriter[BsonNull]
  implicit val bsonObjectIdReadWriter: ReadWriter[BsonObjectId] = identityBsonReadWriter[BsonObjectId]
  implicit val bsonInt32ReadWriter: ReadWriter[BsonInt32] = identityBsonReadWriter[BsonInt32]
  implicit val bsonInt64ReadWriter: ReadWriter[BsonInt64] = identityBsonReadWriter[BsonInt64]
  implicit val bsonDoubleReadWriter: ReadWriter[BsonDouble] = identityBsonReadWriter[BsonDouble]
  implicit val bsonDateTimeReadWriter: ReadWriter[BsonDateTime] = identityBsonReadWriter[BsonDateTime]
  implicit val bsonBinaryReadWriter: ReadWriter[BsonBinary] = identityBsonReadWriter[BsonBinary]
  implicit val bsonSymbolReadWriter: ReadWriter[BsonSymbol] = identityBsonReadWriter[BsonSymbol]

  // TODO: add a test for this
  implicit val dateReadWriter: ReadWriter[Date] = ReadWriter.writing[Date](d => backend.makeDateTime(d.getTime))
    .reading {
      case backend.BsonExtract.DateTime(ts) => new Date(ts)
    }.orThrowing(whenReading = "date", expected = "datetime")

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
