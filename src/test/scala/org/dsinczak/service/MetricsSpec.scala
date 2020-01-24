package org.dsinczak.service

import com.vividsolutions.jts.geom.Coordinate
import org.scalatest.{MustMatchers, WordSpec}

import scala.language.postfixOps

class MetricsSpec extends WordSpec with MustMatchers {

  import scala.concurrent.duration._

  val squareMap: NetworkMapInfo = (for {
    reader <- NetworkMapReader.fromUrn("urn:demo:SquareDemoReader")
    nm <- reader.load
  } yield nm).get

  "Metric calculator" when {
    "vehicle Stations ETA is counted" should {
      val etaSquareCalculator = Metrics.calculateVehicleStationsEtaFactory(squareMap.networkMap) _

      "return proper values for each available station" in {
        etaSquareCalculator(0.1, new Coordinate(0, 5), (1, 3)) mustBe List(
          (1,350 milliseconds),
          (2,250 milliseconds),
          (4,150 milliseconds),
          (3,50 milliseconds)
        )
      }
    }
  }

}
