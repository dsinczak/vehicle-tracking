package org.dsinczak.service

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.vividsolutions.jts.geom.Coordinate
import org.dsinczak.model.TopologicalMap
import org.dsinczak.service.NetworkTopologicalMap.emptyTopologicalMap
import org.dsinczak.service.TopologicalMapCache.GetTopologicalMap
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

class TopologicalMapCacheSpec extends TestKit(ActorSystem("MySpec"))
  with WordSpecLike with MustMatchers with ImplicitSender with BeforeAndAfterAll {

  val demoMap: TopologicalMap = (for {
    reader <- NetworkMapReader.fromUrn("urn:demo:SquareDemoReader")
    nm <- reader.load
    topologicalMap <- emptyTopologicalMap(nm)
  } yield topologicalMap).get

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "TopologicalMap cache" must {
    "start with empty then add and then update vehicle location" in {
      // Given - cache with empty map
      val cache = system.actorOf(TopologicalMapCache.props(demoMap))
      // When
      cache ! GetTopologicalMap
      // Then - empty map should be returned
      expectMsg(demoMap)
      // When - new vehicle location is updated
      system.eventStream.publish(VehicleLocationChanged("bmw", new Coordinate(0.0, 0.1), (1, 3), 0.01))
      cache ! GetTopologicalMap
      // Then - it should be added to vehicles list
      expectMsg(demoMap.copy(vehicles = List(((1,3),"bmw",0.01))))
      // When - existing vehicle location is updated
      system.eventStream.publish(VehicleLocationChanged("bmw", new Coordinate(0.0, 1.0), (1, 3), 0.1))
      cache ! GetTopologicalMap
      // Then - it should be updated in vehicles list
      expectMsg(demoMap.copy(vehicles = List(((1,3),"bmw",0.1))))
    }

  }
}
