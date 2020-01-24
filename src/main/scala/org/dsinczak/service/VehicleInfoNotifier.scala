package org.dsinczak.service

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.dsinczak.model.VehicleInfo

// Create a WebSocket that allow retrieving the real-time location (which is not it's absolute position!)
// of vehicles, which consist of the id of the segment on which the vehicle is, and the relative position
// of this vehicle on his segment
// TODO it would be wise to have single notifier for many recipients.
class VehicleInfoNotifier extends Actor with ActorLogging {

  import VehicleInfoNotifier._

  context.system.eventStream.subscribe(context.self, classOf[VehicleLocationChanged])

  private var notificationRecipient: Option[ActorRef] = None

  override def receive: Receive = {
    case ConnectNotificationRecipient(actorRef) =>
      notificationRecipient = Some(actorRef)
    case StopNotificationRecipient =>
      context.stop(self)
    case VehicleLocationChanged(name, _, segment, location) =>
      notificationRecipient.fold()(_ ! VehicleInfo(name, segment, location))
    case notExpected =>
      log.warning("Message {} was totally unexpected", notExpected)
  }
}
object VehicleInfoNotifier {
  def props(): Props = Props(new VehicleInfoNotifier)

  sealed trait Message
  case class ConnectNotificationRecipient(actorRef: ActorRef) extends Message
  case object StopNotificationRecipient extends Message
}