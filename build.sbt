sbtPlugin := true

name := "sbt-appengine"

organization := "com.cornim"

version := "0.6.3"

description := "sbt plugin to deploy on appengine"

licenses := Seq("MIT License" -> url("https://github.com/sbt/sbt-appengine/blob/master/LICENSE"))

libraryDependencies ++={
  val scalaV = (scalaBinaryVersion in sbtPlugin).value
  val sv = (sbtBinaryVersion in sbtPlugin).value
  Seq(sv match {
    case "0.12" => "cc.spray" % "sbt-revolver" % "0.6.1" extra("scalaVersion" -> scalaV, "sbtVersion" -> sv)
    case "0.13" => "io.spray" % "sbt-revolver" % "0.7.1" extra("scalaVersion" -> scalaV, "sbtVersion" -> sv) 
  }
)}

addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "3.0.1")

scalacOptions := Seq("-deprecation", "-unchecked", "-feature")


publishArtifact in (Compile, packageBin) := true
publishArtifact in (Test, packageBin) := false
publishArtifact in (Compile, packageDoc) := false
publishArtifact in (Compile, packageSrc) := false

resolvers += "Maven.org" at "http://repo1.maven.org/maven2"

resolvers += "spray repo" at "http://repo.spray.cc"

publishMavenStyle := false

publishTo := {
  if (version.value contains "-SNAPSHOT") Some(Resolver.sbtPluginRepo("snapshots"))
  else Some(Resolver.sbtPluginRepo("releases"))
}

