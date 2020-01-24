package org.dsinczak

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.dsinczak.service.NetworkTopologicalMap._
import org.dsinczak.service._
import org.dsinczak.web.WebServer

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object Application extends App {

  implicit val AS: ActorSystem = ActorSystem("system")
  implicit val AM: ActorMaterializer = ActorMaterializer()

  val config = AS.settings.config
  val host = config.getString("app.server.host")
  val port = config.getInt("app.server.port")
  val networkMapSource = config.getString("app.network-map.source")
  val numberOfVehicles = config.getInt("app.vehicle-service.vehicles")

  implicit val retryTimeout: Timeout = Timeout(5 seconds)

  val program = for {
    reader                    <- NetworkMapReader.fromUrn(networkMapSource)
    networkInfo               <- reader.load
    emptyTopologicalMap       <- emptyTopologicalMap(networkInfo)
    topologicalLocationFinder = networkCoordinates2TopologicalLocation(networkInfo)
    topologicalMapActor       = AS.actorOf(TopologicalMapCache.props(emptyTopologicalMap))
    _                         = AS.actorOf(MetricsRecorder.props(networkInfo))
    _                         = new WebServer(host, port)(topologicalMapActor).start()
    _                         = new VehiclesService(networkInfo.networkMap, numberOfVehicles)
    _                         = AS.actorOf(VehicleMessageListener.props(topologicalLocationFinder))
  } yield networkInfo

  program match {
    case Success(network) => AS.log.info("Application initialized for network: {} on http://{}:{}", network, host, port)
    case Failure(exception) =>
      // real deal is here https://doc.akka.io/docs/akka/current/coordinated-shutdown.html
      // its only to pin point the fact that we need to shutdown gracefully
      AS.log.error(exception, "Application initialization error")
      Await.ready(AS.terminate(), 10 seconds)
      System.exit(1)
  }

}
