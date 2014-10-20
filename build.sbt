//2.2.x import play.Project._
//2.3.x
import play.PlayScala

name := "Blog about DSL"

scalaVersion := "2.11.3"

libraryDependencies ++= Seq(
  "com.dslplatform" %% "dsl-client-scala" % "0.9.1",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  //"ws.securesocial" %% "securesocial" % "master-SNAPSHOT",
  "org.scalatest" %% "scalatest" % "2.2.2" % "test"
)

//2.2.x
//playScalaSettings

//2.3.x
lazy val root = (project in file(".")).enablePlugins(PlayScala)
