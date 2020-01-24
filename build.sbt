organization  := "org.dsinczak"

name          := "vehicle-tracking-exercise"

scalaVersion  := "2.13.1"

//resolvers += "mvnrepository" at "http://mvnrepository.com/artifact/"
//resolvers += "central" at "http://repo1.maven.org/maven2/"

libraryDependencies ++= Seq(
  "com.typesafe.akka"          %% "akka-http"            % "10.1.10",
  "com.typesafe.akka"          %% "akka-http-spray-json" % "10.1.10",
  "com.typesafe.akka"          %% "akka-stream"          % "2.5.25",
  "com.typesafe.akka"          %% "akka-actor"           % "2.5.25",
  "org.typelevel"              %% "cats-core"            % "2.0.0",
  "com.vividsolutions"          % "jts"                  % "1.13",
  "org.wololo"                  % "jts2geojson"          % "0.12.0",
  "com.typesafe"                % "config"               % "1.3.3",
  "de.slub-dresden"             % "urnlib"               % "2.0.1",
  "org.scalatest"              %% "scalatest"            % "3.0.8"      % "test",
  "com.typesafe.akka"          %% "akka-testkit"         % "2.5.25" %  "test"
)

