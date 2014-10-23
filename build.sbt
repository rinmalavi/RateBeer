import play.PlayScala

name := "scalabeer"

scalaVersion := "2.11.3"

libraryDependencies ++= Seq(
  "com.dslplatform" %% "dsl-client-scala" % "0.9.1",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  //"ws.securesocial" %% "securesocial" % "master-SNAPSHOT",
  "org.scalatest" %% "scalatest" % "2.2.3" % "test"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)
