package io.github.netvl.picopickle.backends.jawn

import io.github.netvl.picopickle._

import scala.util.{Try, Success, Failure}

trait JsonBackendComponent extends BackendComponent {
  override val backend = JsonAst.Backend
}

trait JsonExceptionsComponent extends ExceptionsComponent {
  this: BackendComponent =>

  case class JsonParseException(message: String, cause: Throwable)
    extends BaseException(message, cause)
}

trait JsonStringSerializationComponent {
  self: Pickler with TypesComponent with JsonBackendComponent with JsonExceptionsComponent =>

  def readAst(str: String): JsonAst.JsonValue = jawn.Parser.parseFromString(str)(JawnFacade) match {
    case Success(r) => r
    case Failure(e) => throw JsonParseException(s"invalid JSON: $str", e)
  }
  def writeAst(ast: JsonAst.JsonValue): String = JsonRenderer.render(ast)

  def readString[T: Reader](str: String): T = read[T](readAst(str))
  def tryReadString[T: Reader](str: String): Try[T] = Try(readString[T](str))

  def writeString[T: Writer](value: T): String = writeAst(write(value))

  class JsonSerializer[T: Reader: Writer] extends Serializer[T] {
    def readString(str: String): T = self.readString[T](str)
    def tryReadString(str: String): Try[T] = self.tryReadString[T](str)

    def writeString(value: T): String = self.writeString(value)
  }

  override def serializer[T: Reader: Writer]: JsonSerializer[T] = new JsonSerializer[T]
}

trait JsonPickler
  extends DefaultPickler
  with JsonBackendComponent
  with JsonStringSerializationComponent
  with JsonExceptionsComponent

object JsonPickler extends JsonPickler
