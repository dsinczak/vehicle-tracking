package org.dsinczak.service

import akka.actor.{Actor, ActorLogging, Props}
import org.dsinczak.model.TopologicalMap
import org.dsinczak.service.TopologicalMapCache.GetTopologicalMap

// Create a REST endpoint that expose a TopologicalMap generated out of the currently
// hard-coded WebServer.network and the position of the vehicles.
// Create a service that store and cache TopologicalMaps using a DB, and integrate it in the application.
// TODO nothing stands in way of adding persistence
class TopologicalMapCache private(emptyTopologicalMap: TopologicalMap) extends Actor with ActorLogging {

  context.system.eventStream.subscribe(context.self, classOf[VehicleLocationChanged])

  var current: TopologicalMap = emptyTopologicalMap

  def receive: Receive = {
    case VehicleLocationChanged(name, _, (from, to), location) =>
      val vehicleIndex = current.vehicles.indexWhere{ case(_, vn, _) => vn == name}
      val newVehicle = ((from, to), name, location)
      val newMap = if(vehicleIndex < 0) {
        log.info(s"New vehicle '$name' with location [(${newVehicle._1._1},${newVehicle._1._2}),${ newVehicle._3}]")
        current.copy(vehicles = current.vehicles.appended(newVehicle))
      } else {
        val oldVehicle = current.vehicles(vehicleIndex)
        log.info(s"Vehicle '$name' location [(${oldVehicle._1._1},${oldVehicle._1._2}),${oldVehicle._3}] changed to [(${newVehicle._1._1},${newVehicle._1._2}),${ newVehicle._3}]")
        current.copy(vehicles = current.vehicles.updated(vehicleIndex, newVehicle))
      }
      current = newMap
    case GetTopologicalMap =>
      sender() ! current
  }
}

object TopologicalMapCache {

  def props(emptyTopologicalMap: TopologicalMap): Props = Props(new TopologicalMapCache(emptyTopologicalMap))

  sealed trait Message
  case object GetTopologicalMap extends Message

}
