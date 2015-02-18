package io.github.netvl.picopickle.backends.jawn

import io.github.netvl.picopickle.{Pickler, BackendComponent, TypesComponent, DefaultPickler}

import scala.util.{Success, Failure}

trait JsonBackendComponent extends BackendComponent {
  override val backend = JsonAst.Backend
}

trait JsonStringSerializationComponent {
  this: Pickler with TypesComponent with JsonBackendComponent =>

  def writeAst(ast: JsonAst.JsonValue): String = JsonRenderer.render(ast)
  def readAst(str: String): JsonAst.JsonValue = jawn.Parser.parseFromString(str)(JawnFacade) match {
    case Success(r) => r
    // TODO: come up with a better exception
    case Failure(e) => throw new IllegalArgumentException(s"Invalid JSON: $str", e)
  }

  def writeString[T: Writer](value: T): String = writeAst(write(value))
  def readString[T: Reader](str: String): T = read[T](readAst(str))
}

trait JsonPickler extends DefaultPickler with JsonBackendComponent with JsonStringSerializationComponent
object JsonPickler extends JsonPickler
