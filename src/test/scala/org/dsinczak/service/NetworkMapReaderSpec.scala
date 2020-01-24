package org.dsinczak.service

import com.vividsolutions.jts.geom.{Coordinate, PrecisionModel}
import org.dsinczak.model.NetworkMap
import org.scalatest.{MustMatchers, WordSpec}

import scala.util.Success

class NetworkMapReaderSpec extends WordSpec with MustMatchers {

  private val simpleGeoJson =
    """
      |{
      |	"type": "FeatureCollection",
      |
      |	"crs": {
      |		"type": "name",
      |		"properties": {
      |			"name": "urn:ogc:def:crs:EPSG::21781"
      |		}
      |	},
      |
      |	"features": [
      | {
      |		"type": "Feature",
      |		"properties": {
      |			"id": null,
      |			"bidir": 0,
      |			"speedMax": 2.500000
      |		},
      |		"geometry": {
      |			"type": "LineString",
      |			"coordinates": [
      |				[0.0, 0.0],
      |				[0.0, 10.0],
      |				[10.0, 10.0],
      |				[10.0, 0.0],
      |				[0.0, 0.0]
      |			]
      |		}
      |	},
      | {
      |		"type": "Feature",
      |		"properties": {
      |			"id": null,
      |			"bidir": 0,
      |			"speedMax": 2.500000
      |		},
      |		"geometry": {
      |			"type": "LineString",
      |			"coordinates": [
      |				[10.0, 10.0],
      |				[20.0, 20.0],
      |       [10.0, 0.0]
      |			]
      |		}
      |	}
      | ]
      |}
      |
    """.stripMargin

  "NetworkMap reader" should {
    "read network map from geo json " in {
      new GeoJsonReader(simpleGeoJson).load.get.networkMap mustBe
        NetworkMap(
          Map(
            0 -> new Coordinate(0.0, 0.0),
            5 -> new Coordinate(20.0, 20.0),
            1 -> new Coordinate(0.0, 10.0),
            6 -> new Coordinate(10.0, 0.0),
            4 -> new Coordinate(10.0, 10.0)
          ),
          List(
            (0, 1),
            (1, 4),
            (4, 6),
            (6, 0),
            (4, 5),
            (5, 6)
          )
        )
    }

    "load demo network map reader" in {
      NetworkMapReader.fromUrn("urn:demo:SquareDemoReader") mustBe Success(SquareDemoReader)
    }

    "fail to load non existing demo reader" in {
      NetworkMapReader.fromUrn("urn:demo:NewYorkReader").isFailure mustBe true
    }

    "load network map reader from classpath and load network map" in {
      // When
      val networkTry = for {
        reader <- NetworkMapReader.fromUrn("urn:classpath:lc-track0-21781%2Egeojson")
        nw <- reader.load
      } yield nw

      // Then
      networkTry.isSuccess mustBe true
      // And - network was loaded
      val networkInfo = networkTry.get
      networkInfo.networkMap.edges.size mustBe 33
      networkInfo.networkMap.nodes.size mustBe 32
    }

    "load network map reader from classpath and load network map with fixed precision" in {
      // When
      val networkTry = for {
        reader <- NetworkMapReader.fromUrn("urn:classpath:lc-track0-21781%2Egeojson?=precision=FIXED,2")
        nw <- reader.load
      } yield nw

      // Then
      networkTry.isSuccess mustBe true
      // And - network was loaded
      val networkInfo = networkTry.get
      networkInfo.precisionModel.getType==PrecisionModel.FIXED
      networkInfo.precisionModel.getScale==100
    }
  }

}
