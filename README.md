# Vehicle tracking assignment

In this exercise, we provided a primitive model defined in `model/package.scala`, which include the domain model you will use.
You will also find a simple service defined in `service/VehiclesService.scala` which generate random vehicle movements for a given `NetworkMap` and forward them through an Akka "event bus".

## Exercise goal:

1. Create a REST endpoint that expose a `TopologicalMap` generated out of the currently hard-coded `WebServer.network` and the position of the vehicles.

2. Create a WebSocket that allow retrieving the real-time *location* (which is not it's absolute position!) of vehicles, which consist of the id of the segment on which the vehicle is, and the relative position of this vehicle on his segment

3. Create a helper function that create a `NetworkMap` out of a GeoJSON file

4. Make your project run with the embedded track `src/main/resources/lc-track0-21781.geojson` and 3 vehicles.

*Want more challenges? here are a few optional tasks:*

5. Create a service that store and cache `TopologicalMap`s using a DB, and integrate it in the application.

6. Create a service that store in a DB:
..the total travelled distance per vehicles
..the average travel time per edges

7. Create a service/endpoint that return the ETA for a given vehicle for all stations

## Some small rules
- You should use git and record the history of your changes.
- You should not have to change the code from `VehiclesService`, you can experiment if needed but the data format shouldn't change.
- You can use any external library you want



