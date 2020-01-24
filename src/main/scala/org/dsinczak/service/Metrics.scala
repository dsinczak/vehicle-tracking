package org.dsinczak.service

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import com.vividsolutions.jts.geom.{Coordinate, LineSegment}
import org.dsinczak.model.NetworkMap

import scala.util.Try

private[service] object Metrics {

  import scala.concurrent.duration._

  /**
   * Function calculates total travelled distance of vehicle. This distance is approximation. It is as good
   * as precision and sampling of vehicle message is.
   *
   * @param previousCoordinate        last known vehicle coordinates
   * @param newCoordinate             new vehicle coordinates
   * @param previousTravelledDistance last known vehicle traveled distance
   * @return new traveled distance
   */
  def calculateVehicleTraveledDistance(previousCoordinate: Option[Coordinate], newCoordinate: Coordinate, previousTravelledDistance: Option[Double]): Double = {
    val lineSegmentDistance = previousCoordinate match {
      case Some(pc) => new LineSegment(pc, newCoordinate).getLength
      case None => 0.0
    }
    previousTravelledDistance.map(distance => distance + lineSegmentDistance).getOrElse(0.0)
  }

  /**
   * Function calculates total traveled time of vehicle.
   *
   * @param currentTime                 current time timestamp
   * @param previousVehicleTimestamp    last known vehicle timestamp
   * @param previousVehicleTraveledTime last known vehicle traveled time
   * @return new vehicle traveled time
   */
  def calculateVehicleTraveledTime(currentTime: LocalDateTime, previousVehicleTimestamp: Option[LocalDateTime], previousVehicleTraveledTime: Option[Duration]): Duration = {
    val locationTimeDifference: Long = previousVehicleTimestamp
      .map(time => ChronoUnit.MILLIS.between(time, currentTime))
      .getOrElse(0)
    previousVehicleTraveledTime.map(time => time + locationTimeDifference.millis).getOrElse(Duration.Zero)
  }

  /**
   * Function calculates average speed in uniform linear motion.
   * TODO in real world measurement units would matter a lot, in this case lets assume its just unit/millisecond
   *
   * @param vehicleTraveledDistance total traveled distance
   * @param vehicleTraveledTime     total traveled time
   * @return average speed in unites per millisecond (or 0.0 when traveled time was below 1 millisecond)
   */
  def calculateVehicleAverageSpeed(vehicleTraveledDistance: Double, vehicleTraveledTime: Duration): Double =
    Try(vehicleTraveledDistance / vehicleTraveledTime.toMillis)
      .filter(_.isFinite)
      .getOrElse(0.0)

  /**
   * Calculate vehicle ETA for every station. This is simplest calculation there is, taking into account only
   * average speed in uniform linear motion.
   *
   * @param networkMap          network map information
   * @param vehicleAverageSpeed current vehicle average speed
   * @param coordinate          vehicle coordinate
   * @param segmentId           current vehicle segment id
   * @return list of station arrival estimation
   */
  def calculateVehicleStationsEtaFactory(networkMap: NetworkMap)(
    vehicleAverageSpeed: Double,
    coordinate: Coordinate,
    segmentId: SegmentId
  ): List[(StationId, Duration)] =
    if (vehicleAverageSpeed <= 0.0) List()
    else calculateVehicleStationsEta(networkMap, vehicleAverageSpeed, coordinate, segmentId)

  private def calculateVehicleStationsEta(networkMap: NetworkMap, vehicleAverageSpeed: Double, initialCoordinate: Coordinate, initialSegmentId: SegmentId): List[(StationId, Duration)] = {
    val (_, to) = initialSegmentId
    val coordinate = initialCoordinate
    val eta = calculateGeometryEta(new LineSegment(coordinate, networkMap.nodes(to)), vehicleAverageSpeed)

    calculateSegmentsEta(to, networkMap, vehicleAverageSpeed, eta, connectedSegments(networkMap, to), List((to, eta)))
  }

  /*
   * I just really wanted to try to do this (ETA calculation). It occurred to be not as easy as I thought, therefore this solution
   * is way from being optimal. I was aiming in direction of tailrec (as with big network map this can end badly) and with more
   * time available it might succeed but as for noe this brut-force approach is implemented.
   * Additionally this solution is not resistant to complex cycles in graphs (checkout app.network-map.source="urn:demo:StarDemoReader")
   */
  private def calculateSegmentsEta(firstStation: StationId, networkMap: NetworkMap, vehicleAverageSpeed: Double, currentEta: Duration, nextSegments: List[SegmentId], acc: List[(StationId, Duration)]): List[(StationId, Duration)] = {
    nextSegments match {
      case (_, to) :: tail if to == firstStation =>
        // infinite loop stop
        calculateSegmentsEta(firstStation, networkMap, vehicleAverageSpeed, currentEta, tail, acc)
      case head :: tail =>
        val (from, to) = head
        val segmentEta = calculateGeometryEta(new LineSegment(networkMap.nodes(from), networkMap.nodes(to)), vehicleAverageSpeed)
        val totalEta = currentEta + segmentEta
        calculateSegmentsEta(firstStation, networkMap, vehicleAverageSpeed, currentEta, tail,
          calculateSegmentsEta(firstStation, networkMap, vehicleAverageSpeed, totalEta, connectedSegments(networkMap, to), (to, totalEta) :: acc)
        )
      case Nil => acc
    }
  }

  private def connectedSegments(networkMap: NetworkMap, stationId: StationId): List[SegmentId] =
    networkMap.edges
      .filter { case (nextFrom, _) => nextFrom == stationId }

  private def calculateGeometryEta(segmentGeometry: LineSegment, vehicleAverageSpeed: Double): Duration =
    (segmentGeometry.getLength/* units */ / vehicleAverageSpeed/*units per ms*/).millis

}
