name := """fyp"""

version := "1.0.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.4"

crossScalaVersions := Seq("2.11.12", "2.12.4")

libraryDependencies ++= Seq(
  guice,
  "org.scala-lang.modules" %% "scala-async" % "0.9.6",
  ws,
  specs2 % Test
).map(_.exclude("org.slf4j", "*"))

evictionWarningOptions in update := EvictionWarningOptions.default
  .withWarnTransitiveEvictions(false)
  .withWarnDirectEvictions(false)
