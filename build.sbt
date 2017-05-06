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
    case "0.11.0" => "com.github.siasia" %% "xsbt-web-plugin" % "0.11.0-0.2.8"
    case "0.11.1" => "com.github.siasia" %% "xsbt-web-plugin" % "0.11.1-0.2.10"
    case "0.11.2" => "com.github.siasia" %% "xsbt-web-plugin" % "0.11.2-0.2.11"
    case "0.11.3" => "com.github.siasia" %% "xsbt-web-plugin" % "0.11.3-0.2.11.1"
    case "0.12"   => "com.github.siasia" %% "xsbt-web-plugin" % "0.12.0-0.2.11.1"
    case "0.13"   => "com.earldouglas" % "xsbt-web-plugin" % "0.4.0" extra("scalaVersion" -> scalaV, "sbtVersion" -> sv)
  },
  sv match {
    case "0.12" => "cc.spray" % "sbt-revolver" % "0.6.1" extra("scalaVersion" -> scalaV, "sbtVersion" -> sv)
    case "0.13" => "io.spray" % "sbt-revolver" % "0.7.1" extra("scalaVersion" -> scalaV, "sbtVersion" -> sv) 
  }
)}

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

