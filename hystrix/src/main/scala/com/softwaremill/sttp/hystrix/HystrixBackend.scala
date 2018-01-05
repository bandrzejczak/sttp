package com.softwaremill.sttp.hystrix

import com.netflix.hystrix.{HystrixCommandGroupKey, HystrixObservableCommand}
import com.softwaremill.sttp.{MonadError, Request, Response, SttpBackend}
import rx.{Observable => JavaObservable}
import rx.lang.scala.{JavaConversions, Observable}

class HystrixBackend[R[_], S](delegate: SttpBackend[R, S])
  extends SttpBackend[R, S] {

  override def send[T](request: Request[T, S]): R[Response[T]] =
    responseMonad.fromObservable(
      JavaConversions.toScalaObservable(
        new SttpHystrixCommand(
          HystrixCommandGroupKey.Factory.asKey("SttpCall"),
          responseMonad.toObservable(delegate.send(request))
        ).observe()
      )
    )

  override def close(): Unit = delegate.close()

  /**
    * The monad in which the responses are wrapped. Allows writing wrapper
    * backends, which map/flatMap over the return value of [[send]].
    */
  override def responseMonad: MonadError[R] = delegate.responseMonad

  class SttpHystrixCommand[T](group: HystrixCommandGroupKey, observable: Observable[T]) extends HystrixObservableCommand[T](group) {

    override def construct(): JavaObservable[T] = {
      JavaConversions.toJavaObservable(observable).asInstanceOf[JavaObservable[T]]
    }
  }
}

