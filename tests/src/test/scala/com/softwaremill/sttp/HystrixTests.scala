package com.softwaremill.sttp

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import com.softwaremill.sttp.asynchttpclient.future.AsyncHttpClientFutureBackend
import com.softwaremill.sttp.hystrix.HystrixBackend
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.language.higherKinds

class HystrixTests
    extends FlatSpec
    with Matchers
    with BeforeAndAfterAll
    with StrictLogging
    with TestHttpServer
    with ForceWrapped {

  override val serverRoutes: Route =
    path("echo") {
      post {
        parameterMap { _ =>
          entity(as[String]) { body: String =>
            complete(body)
          }
        }
      }
    }

  override def port = 51824

  val body = "hystrix test"

  var closeBackends: List[() => Unit] = Nil

  runTests("HttpURLConnection")(HttpURLConnectionBackend(),
    ForceWrappedValue.id)
  runTests("TryHttpURLConnection")(TryHttpURLConnectionBackend(),
    ForceWrappedValue.scalaTry)
  runTests("Akka HTTP")(AkkaHttpBackend.usingActorSystem(actorSystem),
    ForceWrappedValue.future)
  runTests("Async Http Client - Future")(AsyncHttpClientFutureBackend(),
    ForceWrappedValue.future)

  def runTests[R[_]](name: String)(
    backend: SttpBackend[R, Nothing],
    forceResponse: ForceWrappedValue[R]): Unit = {

    closeBackends = (() => backend.close()) :: closeBackends

    implicit val hystrixBackend: SttpBackend[R, Nothing] = new HystrixBackend[R, Nothing](backend)
    implicit val forceResponseImp = forceResponse

    name should "call basic via hystrix" in {
      val response = sttp
        .post(uri"$endpoint/echo")
        .send()
        .force()

      response.unsafeBody shouldBe body
    }
  }

  override protected def afterAll(): Unit = {
    closeBackends.foreach(_())
    super.afterAll()
  }

}
