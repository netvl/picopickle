package io.github.netvl.picopickle

trait ExceptionsComponent {
  this: BackendComponent =>

  abstract class BaseException(message: String, cause: Throwable)
    extends RuntimeException(message, cause)
  
  case class ReadException(message: String, data: backend.BValue, cause: Throwable = null)
    extends BaseException(message, cause)

  object ReadException {
    def apply(reading: String, expected: String, got: backend.BValue): ReadException =
      ReadException(s"reading $reading, expected $expected, got $got", data = got)
  }
}

