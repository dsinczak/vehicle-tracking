package org.dsinczak.web

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.{InternalServerError, RequestTimeout}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import akka.pattern.{AskTimeoutException, ask}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.Timeout
import org.dsinczak.model.{TopologicalMap, VehicleInfo}
import org.dsinczak.service.TopologicalMapCache.GetTopologicalMap
import org.dsinczak.service.VehicleInfoNotifier
import org.dsinczak.service.VehicleInfoNotifier.{ConnectNotificationRecipient, StopNotificationRecipient}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

// TODO this class will become unmaintainable when we add more routes and services and it should be
// refactored when more will be added.
class WebServer(host: String, port: Int)
               (topologicalMapActor: ActorRef)
               (implicit AS: ActorSystem, AM: ActorMaterializer, askTimeout: Timeout) extends Directives with JsonSupport {

  // This is questionable but I usually start with that until
  // some performance tests are done
  implicit val executionContext: ExecutionContext = AS.dispatcher

  def start(): Unit = {
    Http().bindAndHandle(route, host, port)
  }

  private val exceptionHandler = ExceptionHandler {
    case _: AskTimeoutException => complete(HttpResponse(RequestTimeout, entity = s"System was not able to respond in desired time"))
    case ex =>
      AS.log.error(ex, "WTF (What a Terrible Failure)")
      complete(HttpResponse(InternalServerError, entity = s"Something went wrong"))
  }

  import WebServer._

  private[web] val route: Route = handleExceptions(exceptionHandler) {
    path("tracking" / "topologicalMap") {
      get {
        onSuccess(topologicalMapService(topologicalMapActor)) { topologicalMap => complete(topologicalMap) }
      }
    } ~ path("tracking" / "vehicles") {
      get {
        handleWebSocketMessages(vehicleInfoService())
      }
    }
  }
}
object WebServer extends JsonSupport {

  def topologicalMapService(topologicalMapActor: ActorRef)(implicit askTimeout: Timeout): Future[TopologicalMap] = {
    (topologicalMapActor ? GetTopologicalMap)
      .mapTo[TopologicalMap]
  }

  // I'm not proudest of this solution, but I did not want
  // to waste too much time right now researching streams
  def vehicleInfoService()(implicit AS: ActorSystem): Flow[Message, Message, _] = {

    import spray.json._

    import scala.concurrent.duration._

    val viWs: ActorRef = AS.actorOf(VehicleInfoNotifier.props())

    val sink: Sink[Message, NotUsed] =
      Flow[Message]
        .collect { case TextMessage.Strict(str) => str }
        .to(Sink.actorRef(viWs, StopNotificationRecipient))

    val source: Source[Message, NotUsed] =
      Source
        .actorRef(bufferSize = 10, overflowStrategy = OverflowStrategy.dropBuffer)
        .map((vi: VehicleInfo) => TextMessage.Strict(vi.toJson.compactPrint))
        .mapMaterializedValue { wsHandle =>
          viWs ! ConnectNotificationRecipient(wsHandle)
          NotUsed
        }
        .keepAlive(maxIdle = 10 seconds, () => TextMessage.Strict("Keep-alive message sent to WebSocket recipient"))

    Flow.fromSinkAndSource(sink, source)
  }
}
