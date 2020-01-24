package org.dsinczak.service

import com.vividsolutions.jts.geom.{Coordinate, LineSegment}
import org.dsinczak.model.TopologicalMap
import org.dsinczak.service.NetworkTopologicalMap._
import org.scalatest.{MustMatchers, WordSpec}

import scala.util.Success

class NetworkTopologicalMapSpec extends WordSpec with MustMatchers {

  val demoMap: NetworkMapInfo = (for {
    reader <- NetworkMapReader.fromUrn("urn:demo:SquareDemoReader")
    nm <- reader.load
  } yield nm).get

  val testMap: NetworkMapInfo = (for {
    reader <- NetworkMapReader.fromUrn("urn:classpath:lc-track0-21781%2Egeojson")
    nm <- reader.load
  } yield nm).get

  private val demoTopologicalMap = TopologicalMap(
    Map(1 -> "Station(0.0, 0.0)", 2 -> "Station(10.0, 0.0)", 3 -> "Station(0.0, 10.0)", 4 -> "Station(10.0, 10.0)"),
    List((1, 3, 1.0), (3, 4, 1.0), (4, 2, 1.0), (2, 1, 1.0)),
    List()
  )

  "Network to Topological mapping" when {
    "creates empty topological representation of network" should {

      "give proper representation of demoMap" in {
        emptyTopologicalMap(demoMap) mustBe Success(demoTopologicalMap)
      }

      "give proper representation of testMap from geojson" in {
        // When
        val result = emptyTopologicalMap(testMap).get

        // Then
        result.stations.size mustBe testMap.networkMap.nodes.size
        result.segments.size mustBe testMap.networkMap.edges.size
        result.vehicles mustBe empty
      }
    }

    "calculates new vehicle topological position" should {
      val topologicalLocationFinder = networkCoordinates2TopologicalLocation(demoMap)

      "find proper location" in {
        topologicalLocationFinder(new Coordinate(0.0, 0.1)) mustBe Some((1, 3, 0.01))
        topologicalLocationFinder(new Coordinate(0.0, 1.0)) mustBe Some((1, 3, 0.1))
        topologicalLocationFinder(new Coordinate(66, 66)) mustBe None
      }
    }

    "finds vehicle position on network map" should {
      "match edge to coordinates" in {
        val check = isPointOnLineSegment(0.02, _, _)
        check(new Coordinate(9.23, 5, 78),
          new LineSegment(new Coordinate(7.5, 7.5), new Coordinate(10, 5))
        ) mustBe false

        check(new Coordinate(2.43, 7.42),
          new LineSegment(new Coordinate(0, 5), new Coordinate(2.5, 7.5))
        ) mustBe true

        check(new Coordinate(4.7, 0.3),
          new LineSegment(new Coordinate(5, 0), new Coordinate(2.5, 2.5))
        ) mustBe true

      }
    }
  }

}
