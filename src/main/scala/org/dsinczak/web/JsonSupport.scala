package org.dsinczak.web

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.dsinczak.model.{TopologicalMap, VehicleInfo}
import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, JsonFormat, RootJsonFormat}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  // This implementation is due to bug in spray that does not allow Maps with keys other than String
  implicit val stationsFormat: JsonFormat[Map[Int, String]] = new JsonFormat[Map[Int, String]] {
    override def write(obj: Map[Int, String]): JsValue = JsObject(obj.map { case (k, v) => k.toString -> JsString(v) })

    override def read(json: JsValue): Map[Int, String] = ???
  }
  implicit val topologicalMapFormat: RootJsonFormat[TopologicalMap] = jsonFormat3(TopologicalMap.apply)
  implicit val vehicleInfo: RootJsonFormat[VehicleInfo] = jsonFormat3(VehicleInfo.apply)
}
