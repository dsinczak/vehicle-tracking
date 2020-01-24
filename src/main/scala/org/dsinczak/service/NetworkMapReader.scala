package org.dsinczak.service

import java.io.Closeable

import com.vividsolutions.jts.geom.{Coordinate, PrecisionModel}
import de.slub.urn.{URN, URN_8141}
import org.dsinczak.model.NetworkMap
import org.wololo.geojson.{Feature, LineString}

import scala.io.{BufferedSource, Source}
import scala.util.{Success, Try}

sealed trait NetworkMapReader {
  def load: Try[NetworkMapInfo]
}

object NetworkMapReader {

  def fromUrn(rawUrn: String): Try[NetworkMapReader] = Try {
    val urn = URN.rfc8141().parse(rawUrn)
    val uni = urn.namespaceIdentifier().toString
    val uss = urn.namespaceSpecificString().unencoded()
    uni.toLowerCase() match {
      case "demo"      => loadDemo(uss)
      case "classpath" => read(Source.fromResource(uss))(geoJsonReader(urn))
      case "file"      => read(Source.fromFile(uss))(geoJsonReader(urn))
      case wtf         => throw new IllegalArgumentException(s"NetworkMapReader scheme: $wtf is unknown. Allowed: 'demo', 'classpath', 'file'")
    }
  }

  private def geoJsonReader(urn: URN_8141) = {
    val precisionModelRaw = Option(urn.getRQFComponents.queryParameters().get("precision")).getOrElse("FLOATING")
    val distanceErrorMargin = Option(urn.getRQFComponents.queryParameters().get("distanceErrorMargin")).map(_.toDouble).getOrElse(0.02)
    source: BufferedSource => new GeoJsonReader(source.getLines().mkString, precisionModel(precisionModelRaw), distanceErrorMargin)
  }

  private def precisionModel(precisionModelRaw: String): PrecisionModel = {
    val slitted = precisionModelRaw.split(",")
    slitted(0) match {
      case "FLOATING" => new PrecisionModel(PrecisionModel.FLOATING)
      case "FIXED" =>
        val decimalPlaces = Try(slitted(1).toInt) match {
          case Success(places) if places >= 0 => places
          case _ => throw new IllegalArgumentException("FIXED precision model must be int value >= 0")
        }
        new PrecisionModel(("1" + ("0" * decimalPlaces)).toInt)
    }
  }

  private def loadDemo(name: String): NetworkMapReader = name match {
    case "SquareDemoReader" => SquareDemoReader
    case "StarDemoReader"   => StarDemoReader
    case wtf => throw new IllegalArgumentException(s"Demo NetworkMapReader named: $wtf is unknown")
  }

  private def read[T <: Closeable](t: T)(consumer: T => GeoJsonReader): GeoJsonReader =
    try {
      consumer(t)
    } finally {
      t.close()
    }
}

object SquareDemoReader extends NetworkMapReader {
  override def load: Try[NetworkMapInfo] = Success(NetworkMapInfo(
    NetworkMap(
      nodes = Map(
        (1, new Coordinate(0, 0)),
        (2, new Coordinate(10, 0)),
        (3, new Coordinate(0, 10)),
        (4, new Coordinate(10, 10))
      ),
      edges = List(
        (1, 3), (3, 4), (4, 2), (2, 1)
      )
    ), new PrecisionModel(100), 0.01))
}
// Some more sophisticated example but still easy to reason.
object StarDemoReader extends NetworkMapReader {
  override def load: Try[NetworkMapInfo] = Success(NetworkMapInfo(
    NetworkMap(
      nodes = Map(
        (1, new Coordinate(2.5, 2.5)),
        (2, new Coordinate(0, 5)),
        (3, new Coordinate(2.5, 7.5)),
        (4, new Coordinate(5, 10)),
        (5, new Coordinate(7.5, 7.5)),
        (6, new Coordinate(10, 5)),
        (7, new Coordinate(7.5, 2.5)),
        (8, new Coordinate(5, 0))
      ),
      edges = List(
        // outer star
        (1, 2), (2, 3), (3, 4), (4, 5),
        (5, 6), (6, 7), (7, 8), (8, 1),
        // inner rectangle
        (1,3), (3,5), (5,7), (7,1)
      )
    ), new PrecisionModel(100), 0.02))
}

/**
 * Create a helper function that create a NetworkMap out of a GeoJSON file.
 *
 * There is strong assumption here that we are only dealing with line strings.
 */
class GeoJsonReader(geoJson: String, precisionModel: PrecisionModel, distanceErrorMargin: Double) extends NetworkMapReader {

  import GeoJsonReader._
  import org.wololo.geojson.{FeatureCollection, GeoJSONFactory}

  def this(geoJson: String) = this(geoJson, new PrecisionModel(PrecisionModel.FLOATING), 0.0)

  override def load: Try[NetworkMapInfo] = for {
    featureCollection <- parse
    features          = featureCollection.getFeatures
    networkMap        <- features2NetworkMap(features.toList)
  } yield NetworkMapInfo(networkMap, precisionModel, distanceErrorMargin)

  private def parse: Try[FeatureCollection] = Try(GeoJSONFactory.create(geoJson).asInstanceOf[FeatureCollection])

  private def features2NetworkMap(features: List[Feature]): Try[NetworkMap] = Try {
    // Assumption is right now we support only line string feature
    val lineStrings = features
      .filter(isLineString)
      .map(toLineString)

    // Take all coordinates from all line-strings and index them
    val nodes = lineStrings
      .flatMap(lineString2NodesNoDuplicates(precisionModel))
      .zipWithIndex
      .map { case (coordinate, idx) => coordinate -> idx }
      .toMap

    // Take all line-strings
    // Then cut them to edges
    // And change coordinates to indexes
    val edges = lineStrings
      .map(lineString2Nodes(precisionModel))
      .map(coordinates2Edges)
      .flatMap(coordinateEdges2IndexedEdges(nodes))

    NetworkMap(nodes.map(_.swap), edges)
  }

}

object GeoJsonReader {

  // is feature a LineString
  val isLineString: Feature => Boolean = f => f.getGeometry.isInstanceOf[LineString]
  // cast feature to LineString
  val toLineString: Feature => LineString = f => f.getGeometry.asInstanceOf[LineString]

  // get all coordinates from line string in raw form: double[][]
  // where first dimension goes through nodes and second always has
  // two elements X and Y. Then map it to Coordinate.
  def lineString2Nodes(precisionModel: PrecisionModel): LineString => Seq[Coordinate] = ls =>
    ls.getCoordinates
      .map(rawCoordinate => {
        val coordinate = new Coordinate(rawCoordinate(0), rawCoordinate(1))
        precisionModel.makePrecise(coordinate)
        coordinate
      })

  // the same as lineString2Nodes but without duplicate nodes
  def lineString2NodesNoDuplicates(precisionModel: PrecisionModel): LineString => Set[Coordinate] = lineString2Nodes(precisionModel).andThen(_.toSet)

  // pair nodes to form edges.
  val coordinates2Edges: Seq[Coordinate] => Seq[(Coordinate, Coordinate)] = coordinates => coordinates zip coordinates.tail

  // HOF that transforms edges with coordinates to edges with nodes from indexed map
  def coordinateEdges2IndexedEdges(nodeIndex: Map[Coordinate, Int]): Seq[(Coordinate, Coordinate)] => Seq[(Int, Int)] =
    tupledCoordinates =>
      tupledCoordinates.map { case (from, to) => (nodeIndex(from), nodeIndex(to)) }

}


