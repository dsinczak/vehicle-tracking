package org.dsinczak.service

import akka.actor.{Actor, ActorLogging, Props}
import com.vividsolutions.jts.geom.Coordinate
import org.dsinczak.model.VehicleMessage

/**
 * This actor can be easily "scaled as it is stateless". Therefore there should also be a easy way
 * of using only akka-streams approach. Unfortunately i do not have enough knowledge to do it in
 * short time. All i all this actor is a flow where: source is:  context.system.eventStream then
 * one transformation is applied and sink is again context.system.eventStream.
 */
class VehicleMessageListener(topologicalLocationFinder: Coordinate => Option[SegmentVehicleLocation]) extends Actor with ActorLogging {

  context.system.eventStream.subscribe(context.self, classOf[VehicleMessage])

  override def receive: Receive = {
    case vm@VehicleMessage(name, coordinate) =>
      topologicalLocationFinder(coordinate) match {
        case Some((from, to, relativeLocation)) =>
          context.system.eventStream.publish(VehicleLocationChanged(name, coordinate, (from, to), relativeLocation))
        case None =>
          log.error("Unable to find topological location vehicle message {}", vm)
      }
  }
}

object VehicleMessageListener {
  def props(topologicalLocationFinder: Coordinate => Option[SegmentVehicleLocation]): Props = Props(new VehicleMessageListener(topologicalLocationFinder))
}
