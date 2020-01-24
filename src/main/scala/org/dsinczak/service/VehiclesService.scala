package org.dsinczak.service

import akka.actor.ActorSystem
import com.vividsolutions.jts.geom.Coordinate
import org.dsinczak.model.{NetworkMap, VehicleMessage}

import scala.math._

class VehiclesService(network: NetworkMap, vehicles: Int)(implicit AS: ActorSystem) {

  import VehiclesService._

  private val rng = new scala.util.Random(0)

  private val names: Array[String] = ('a' to 'z').take(vehicles).map(_.toString).toArray

  // inital network location per vehicle, start on random edges
  private val locations: Array[NetworkLocation] = (0 to vehicles).map { _ =>
    val edge = network.edges(rng.nextInt(network.edges.size))
    NetworkLocation(edge, 0.0)
  }.toArray

  // Hacky simulation system.
  new Thread {
    override def run {
      while (true) {
        Thread.sleep(250)
        locations.synchronized {
          (0 until vehicles).foreach { vehicleIdx =>
            val NetworkLocation((source, target), pos0) = locations(vehicleIdx)

            // distance to travel (between 0.1 and 0.17)
            val distance = (1 + rng.nextInt(8)).toDouble / 100
            // new position on edge after applying distance
            val pos1 = min(pos0 + distance, 1.0)

            if (pos1 >= 1.0) { // if position change goes outside of edge
              // find all edges connected to source one
              val edges = network.edges.filter { case (src, _) => src == target }
              // take random
              // TODO this is place of exception when dealing with networks that are not closed shapes
              val edge = edges(rng.nextInt(edges.size))
              // and put vehicle at the beginning
              locations.update(vehicleIdx, NetworkLocation(edge, 0.0))
            } else { // is position change is inside the edge
              // update vehicle position on edge
              locations.update(vehicleIdx, NetworkLocation((source, target), pos1))
            }

            val coordinate = {
              val sourceCoord: Coordinate = network.nodes(source)
              val targetCoord: Coordinate = network.nodes(target)

              val dx = sourceCoord.x - targetCoord.x
              val dy = sourceCoord.y - targetCoord.y
              val length = sqrt(dx * dx + dy * dy) * pos1

              // The heart of all problems with precision
              def round(d: Double, i: Int) = rint(d * i) / 100

              val theta = atan2(targetCoord.y - sourceCoord.y, targetCoord.x - sourceCoord.x)

              new Coordinate(
                round(sourceCoord.x + (cos(theta) * length), 100),
                round(sourceCoord.y + (sin(theta) * length), 100)
              )
            }

            AS.eventStream.publish(VehicleMessage(names(vehicleIdx), coordinate))
          }
        }
      }
    }
  }.start
}

object VehiclesService {

  case class NetworkLocation(edge: (Int, Int), position: Double)

}
