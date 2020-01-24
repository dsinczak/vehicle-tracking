package org.dsinczak.service

import cats.Eval
import com.vividsolutions.jts.geom.{Coordinate, LineSegment, PrecisionModel}
import org.dsinczak.model.{NetworkMap, TopologicalMap}

import scala.util.Try

/**
 * This is an attempt to model business logic as simple functions and keep it away from actor infrastructure.
 * This simplifies testing approach.
 */
object NetworkTopologicalMap {

  /**
   * Function that takes network and tries to create its topological representation.
   */
  val emptyTopologicalMap: NetworkMapInfo => Try[TopologicalMap] = networkMap => Try(network2Topological(networkMap.networkMap))

  /**
   * HOF that for given network creates a function which is able to calculate topological location basing on
   * coordinate. Due to many reasons finding process may fail therefore the result is an Option.
   * In more 'production' solution with better understanding of domain, this actor would probably
   * return Either[Error, SegmentVehicleLocation]
   * TODO one simple optimization could be could be adding of last segment id as parameter. First check will
   * then try last know segment and if failed then search other line segments.
   */
  val networkCoordinates2TopologicalLocation: NetworkMapInfo => Coordinate => Option[SegmentVehicleLocation] = {
    networkMap => {
      // I used eval so this calculation will happen in actor and
      // not during initialization in Application
      val lineSegments = networkMap2LineSegments(networkMap.networkMap)
      val toIntersectingLine = findIntersectingLine(networkMap.distanceErrorMargin) _
      vehicleCoordinate => {
        val vehicleCoordinateWithPrecision = new Coordinate(vehicleCoordinate)
        networkMap.precisionModel.makePrecise(vehicleCoordinateWithPrecision)
        lineSegments
          .map(toIntersectingLine(vehicleCoordinateWithPrecision))
          .map(toSegmentVehicleLocation(networkMap.precisionModel, vehicleCoordinateWithPrecision))
          .value
      }
    }
  }

  private def toSegmentVehicleLocation(precisionModel: PrecisionModel, coordinate: Coordinate): Option[SegmentGeometry] => Option[SegmentVehicleLocation] =
    _.map { case (fromId, toId, lineSegment) => (fromId, toId, precisionModel.makePrecise(lineSegment.segmentFraction(coordinate))) }

  /**
   * Checks if the point is located on the given `LineSegment` (including endpoints).
   * Very 'naive' and basic check it point belongs to line segment.
   */
  private[service] def isPointOnLineSegment(distanceErrorMargin: Double, point: Coordinate, line: LineSegment): Boolean = {
    val lengthOfLine =line.getLength
    val distFromEnd1 = point.distance(line.p0)
    val distFromEnd2 = point.distance(line.p1)
    val sumOfDistances = distFromEnd1 + distFromEnd2
    if (Math.abs(sumOfDistances - lengthOfLine) <= distanceErrorMargin) return true

    false
  }

  private def findIntersectingLine(distanceErrorMargin: Double)(coordinate: Coordinate): SegmentGeometries => Option[SegmentGeometry] =
    lineSegments => lineSegments.find { case (_, _, lineSegment) => isPointOnLineSegment(distanceErrorMargin, coordinate, lineSegment) }

  private def networkMap2LineSegments(networkMap: NetworkMap): Eval[SegmentGeometries] = Eval.later {
    networkMap.edges
      .map { case (fromId, toId) => (fromId, toId, networkMap.nodes(fromId), networkMap.nodes(toId)) }
      .map { case (fromId, toId, fromCoordinate, toCoordinate) => (fromId, toId, new LineSegment(fromCoordinate, toCoordinate)) }
  }

  private def network2Topological(networkMap: NetworkMap): TopologicalMap = {
    val stations = networkMap.nodes.map(node2Station)
    val segments = networkMap.edges.map(edge2Segment(networkMap))
    val normalizedSegments = segments.map(normalizeSegmentSize(segments.map(_._3).max))

    TopologicalMap(stations, normalizedSegments, List())
  }

  private val node2Station: Node => Station = {
    case (id, coordinate) => id -> s"Station(${coordinate.x}, ${coordinate.y})"
  }

  private def edge2Segment(networkMap: NetworkMap): Edge => Segment = {
    case (from, to) =>
      val fromCoordinate = networkMap.nodes(from)
      val toCoordinate = networkMap.nodes(to)
      val distance = fromCoordinate.distance(toCoordinate)
      (from, to, distance)
  }

  private def normalizeSegmentSize(maxSize: Double): Segment => Segment = {
    case (from, to, distance) => (from, to, Try(distance / maxSize).getOrElse(0.0))
  }

}
