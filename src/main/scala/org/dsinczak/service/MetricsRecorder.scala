package org.dsinczak.service

import java.time.LocalDateTime

import akka.actor.{Actor, ActorLogging, Props}
import com.vividsolutions.jts.geom.Coordinate
import org.dsinczak.service.Metrics.{calculateVehicleAverageSpeed, calculateVehicleTraveledDistance, calculateVehicleTraveledTime}

import scala.collection.mutable
import scala.language.postfixOps

/**
 * IMPORTANT: normally such analysis tasks can be done using 'ready to go' tools like
 * prometheus+grafana. Application responsibility is generate proper metrics, data storing
 * and analysis tool are already there.
 *
 * Create a service that store in a DB:
 * - the total travelled distance per vehicles
 * - the average travel time per edges
 * - ETA for a given vehicle for all stations
 */
class MetricsRecorder(networkMapInfo: NetworkMapInfo) extends Actor with ActorLogging {

  import MetricsRecorder._

  import scala.concurrent.duration._

  context.system.eventStream.subscribe(context.self, classOf[VehicleLocationChanged])

  context.system.scheduler.schedule(2 seconds, 1 seconds, self, PersistMetrics)(context.dispatcher)

  private val calculateVehicleStationsEta = Metrics.calculateVehicleStationsEtaFactory(networkMapInfo.networkMap) _

  val currentVehicleState:      mutable.Map[VehicleName, (Coordinate, LocalDateTime)] = mutable.Map()
  val vehicleTravelledDistance: mutable.Map[VehicleName, Double] = mutable.Map()
  val vehicleTravelledTime:     mutable.Map[VehicleName, Duration] = mutable.Map()
  val vehicleAverageSpeed:      mutable.Map[VehicleName, Double] = mutable.Map()
  val vehicleStationEta:        mutable.Map[VehicleName, List[(StationId, Duration)]] = mutable.Map()

  override def receive: Receive = {
    case VehicleLocationChanged(name, coordinate, segmentId, _) =>
      updateVehicleTraveledDistance(name, coordinate)
      val currentTime = LocalDateTime.now()
      updateVehicleTravelledTime(name, currentTime)
      updateVehicleAverageSpeed(name)
      updateVehicleStationsEta(name, coordinate, segmentId)
      currentVehicleState.update(name, (coordinate, currentTime))
    // TODO other metrics calculation
    case PersistMetrics =>
      persistMetrics()

  }

  private def persistMetrics(): Unit = {
    // TODO I suppose persist command would be send to another actor responsible for
    // persistence. Metrics update can be done (at least this is my assumption) periodically
    vehicleTravelledDistance foreach { case (name, distance) => log.info("Vehicle {} total travel distance {}", name, distance) }
    vehicleTravelledTime     foreach { case (name, time)     => log.info("Vehicle {} total time traveled {} ms", name, time.toMillis) }
    vehicleAverageSpeed      foreach { case (name, speed)    => log.info("Vehicle {} average speed {} unit/ms", name, speed) }
    vehicleStationEta        foreach { case (name, etas)     => log.info("Vehicle {} stations ETAs: {}", name,
      etas.map { case (station, eta) => s"(StationId: $station ETA: ${eta.toMillis}ms)" }.mkString("[", ", ", "]")) }
  }

  private def updateVehicleTraveledDistance(name: VehicleName, coordinate: Coordinate): Unit =
    vehicleTravelledDistance.update(name, calculateVehicleTraveledDistance(
      currentVehicleState.get(name).map(_._1),
      coordinate,
      vehicleTravelledDistance.get(name),
    ))

  private def updateVehicleTravelledTime(name: VehicleName, currentTime: LocalDateTime): Unit =
    vehicleTravelledTime.update(name, calculateVehicleTraveledTime(
      currentTime,
      currentVehicleState.get(name).map(_._2),
      vehicleTravelledTime.get(name)
    ))


  private def updateVehicleAverageSpeed(name:VehicleName): Unit =
    vehicleAverageSpeed.update(name, calculateVehicleAverageSpeed(
    vehicleTravelledDistance(name),
    vehicleTravelledTime(name))
    )

  private def updateVehicleStationsEta(name: VehicleName, coordinate: Coordinate, segmentId: SegmentId): Unit = {
    vehicleStationEta.update(name, calculateVehicleStationsEta(
      vehicleAverageSpeed(name),
      coordinate,
      segmentId
    ))
  }
}

object MetricsRecorder {

  def props(networkMapInfo: NetworkMapInfo) = Props(new MetricsRecorder(networkMapInfo))

  private case object PersistMetrics


}
