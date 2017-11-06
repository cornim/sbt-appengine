sbtPlugin := true

name := "sbt-appengine"

organization := "com.cornim"

version := "0.8.1"

description := "sbt plugin to deploy on appengine"

licenses := Seq("MIT License" -> url("https://github.com/sbt/sbt-appengine/blob/master/LICENSE"))

addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "3.0.1")

scalacOptions := Seq("-deprecation", "-unchecked", "-feature")

