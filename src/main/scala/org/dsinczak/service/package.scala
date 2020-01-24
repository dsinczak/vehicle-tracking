package org.dsinczak

import com.vividsolutions.jts.geom.{Coordinate, LineSegment, PrecisionModel}
import org.dsinczak.model.NetworkMap

package object service {
  // Station identifier
  type StationId = Int
  // Segment size relative to all segments in topological map
  type SegmentSize = Double
  // Relative location on segment (from 0.0 to 1.0)
  type SegmentLocation = Double
  type VehicleName = String

  // Int         - Source station identifier
  // Int         - Target station identifier
  // LineSegment - geometrical representation of line
  type SegmentGeometry = (StationId, StationId, LineSegment)
  type SegmentGeometries = List[SegmentGeometry]

  // Directed segments connecting stations.
  // StationId        - Source station identifier
  // StationId        - Target station identifier
  // SegmentSize      - Relative size (between 0.0 and 1.0)
  type Segment = (StationId, StationId, SegmentSize)

  // StationId        - Source station identifier
  // StationId        - Target station identifier
  type SegmentId = (StationId, StationId)

  // StationId        - Segment identifier (source) where the vehicle is currently located
  // StationId        - Segment identifier (target) where the vehicle is currently located
  // SegmentLocation  - Position of the vehicle relative to this segment (between 0.0 and 1.0)
  type SegmentVehicleLocation = (StationId, StationId, SegmentLocation)

  // Int        - Node identifier.
  // Coordinate - Location of the node.
  type Node = (Int, Coordinate)
  // Int        - Station Identifier
  // String     - Station Name
  type Station = (Int, String)
  // Int        - Source station identifier
  // Int        - Target station identifier
  type Edge = (Int, Int)

  /**
   * Representation of network ma with additional meta information that describe it.
   */
  case class NetworkMapInfo(networkMap:NetworkMap, precisionModel: PrecisionModel, distanceErrorMargin: Double)

  /**
   * Business event that represents change of a vehicle location. It carries the same information as vehicle message
   * enriched with current topological location.
   */
  case class VehicleLocationChanged(name: VehicleName, coordinate: Coordinate, segment: SegmentId, location: SegmentLocation)

}
