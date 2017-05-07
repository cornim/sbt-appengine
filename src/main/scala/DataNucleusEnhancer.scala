package sbtappengine

import sbt.AutoPlugin
import sbt.inConfig
import sbt.Keys
import sbt.Def
import sbt.Compile

/*
object DataNucleusEnhancerPlugin extends AutoPlugin {

    lazy val enhance = TaskKey[Unit]("appengine-enhance", "Execute ORM enhancement.")
    lazy val enhanceCheck = TaskKey[Unit]("appengine-enhance-check", "Just check the classes for enhancement status.")
  
    lazy val baseAppengineDataNucleusSettings: Seq[Def.Setting[_]] = Seq(
    packageWar := ((packageWar) dependsOn gae.enhance).value,
    gae.classpath := {
      val appengineORMJars = (gae.libPath.value / "orm" * "*.jar").get
      gae.classpath.value ++ appengineORMJars.classpath
    },
    gae.enhance := {
      val r: ScalaRun = (runner in Runtime).value
      val main: String = (mainClass in gae.enhance).value.get
      val files: Seq[File] = (exportedProducts in Runtime).value flatMap { dir =>
        (dir.data ** "*.class").get ++ (dir.data ** "*.jdo").get
      }
      val args: Seq[String] = (scalacOptions in gae.enhance).value ++ (files map { _.toString })
      r.run(main, (fullClasspath in gae.enhance).value map { _.data }, args, streams.value.log)
    },
    gae.enhanceCheck := {
      val r: ScalaRun = (runner in Runtime).value
      val main: String = (mainClass in gae.enhance).value.get
      val files: Seq[File] = (exportedProducts in Runtime).value flatMap { dir =>
        (dir.data ** "*.class").get ++ (dir.data ** "*.jdo").get
      }
      val args: Seq[String] = (scalacOptions in gae.enhance).value ++ Seq("-checkonly") ++ (files map { _.toString })
      r.run(main, (fullClasspath in gae.enhance).value map { _.data }, args, streams.value.log)
    },
    mainClass in gae.enhance := Some("org.datanucleus.enhancer.DataNucleusEnhancer"),
    fullClasspath in gae.enhance := {
      val appengineORMEnhancerJars = (gae.libPath.value / "tools" / "orm" * "datanucleus-enhancer-*.jar").get ++
        (gae.libPath.value / "tools" / "orm" * "asm-*.jar").get
      (Seq(gae.apiToolsPath.value) ++ appengineORMEnhancerJars).classpath ++ (fullClasspath in Compile).value
    },
    // http://www.datanucleus.org/products/accessplatform_2_2/enhancer.html
    scalacOptions in gae.enhance := ((logLevel in gae.enhance) match {
      case Level.Debug => Seq("-v")
      case _ => Seq()
    }) ++ Seq("-api", (gae.persistenceApi in gae.enhance).value),
    logLevel in gae.enhance := Level.Debug,
    gae.persistenceApi in gae.enhance := "JDO")
  
  lazy val appengineDataNucleusSettings: Seq[Def.Setting[_]] = inConfig(Compile)(baseAppengineDataNucleusSettings)
}*/ 