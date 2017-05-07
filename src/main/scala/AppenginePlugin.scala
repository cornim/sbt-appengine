package sbtappengine

import sbt._
import sbt.Attributed.data
import complete.DefaultParsers.spaceDelimited
import com.earldouglas.xwp.WarPlugin
import com.earldouglas.xwp.WebappPlugin.autoImport.webappPrepare

object AppenginePlugin extends AutoPlugin {
  override def requires = WarPlugin

  import Keys._
  import Def.Initialize

  object AppengineKeys {
    lazy val requestLogs = InputKey[Unit]("appengine-request-logs", "Write request logs in Apache common log format.")
    lazy val rollback = InputKey[Unit]("appengine-rollback", "Rollback an in-progress update.")
    lazy val deploy = InputKey[Unit]("appengine-deploy", "Create or update an app version.")
    lazy val deployBackends = InputKey[Unit]("appengine-deploy-backends", "Update the specified backend or all backends.")
    lazy val rollbackBackend = InputKey[Unit]("appengine-rollback-backends", "Roll back a previously in-progress update.")
    lazy val configBackends = InputKey[Unit]("appengine-config-backends", "Configure the specified backend.")
    lazy val startBackend = InputKey[Unit]("appengine-start-backend", "Start the specified backend.")
    lazy val stopBackend = InputKey[Unit]("appengine-stop-backend", "Stop the specified backend.")
    lazy val deleteBackend = InputKey[Unit]("appengine-delete-backend", "Delete the specified backend.")
    lazy val deployIndexes = InputKey[Unit]("appengine-deploy-indexes", "Update application indexes.")
    lazy val deployCron = InputKey[Unit]("appengine-deploy-cron", "Update application cron jobs.")
    lazy val deployQueues = InputKey[Unit]("appengine-deploy-queues", "Update application task queue definitions.")
    lazy val deployDos = InputKey[Unit]("appengine-deploy-dos", "Update application DoS protection configuration.")
    lazy val cronInfo = InputKey[Unit]("appengine-cron-info", "Displays times for the next several runs of each cron job.")
    lazy val devServer = InputKey[Process]("gae-dev-server", "Run application through development server.")
    lazy val stopDevServer = TaskKey[Unit]("gae-stop-dev-server", "Stop development server.")

    lazy val onStartHooks = SettingKey[Seq[() => Unit]]("appengine-on-start-hooks")
    lazy val onStopHooks = SettingKey[Seq[() => Unit]]("appengine-on-stop-hooks")
    lazy val apiToolsJar = SettingKey[String]("appengine-api-tools-jar", "Name of the development startup executable jar.")
    lazy val apiToolsPath = TaskKey[File]("appengine-api-tools-path", "Path of the development startup executable jar.")
    lazy val sdkVersion = SettingKey[String]("appengine-sdk-version")
    lazy val sdkPath = TaskKey[File]("appengine-sdk-path", "Sets sdk path and retrives sdk if necessary.")
    lazy val classpath = TaskKey[Classpath]("appengine-classpath")
    lazy val apiJarName = SettingKey[String]("appengine-api-jar-name")
    lazy val apiLabsJarName = SettingKey[String]("appengine-api-labs-jar-name")
    lazy val binPath = TaskKey[File]("appengine-bin-path")
    lazy val libPath = TaskKey[File]("appengine-lib-path")
    lazy val libUserPath = TaskKey[File]("appengine-lib-user-path")
    lazy val libImplPath = TaskKey[File]("appengine-lib-impl-path")
    lazy val apiJarPath = TaskKey[File]("appengine-api-jar-path")
    lazy val appcfgName = SettingKey[String]("appengine-appcfg-name")
    lazy val appcfgPath = TaskKey[File]("appengine-appcfg-path")
    lazy val overridePath = TaskKey[File]("appengine-override-path")
    lazy val overridesJarPath = TaskKey[File]("appengine-overrides-jar-path")
    lazy val agentJarPath = TaskKey[File]("appengine-agent-jar-path")
    lazy val emptyFile = TaskKey[File]("appengine-empty-file")
    lazy val localDbPath = SettingKey[File]("appengine-local-db-path")
    lazy val debug = SettingKey[Boolean]("appengine-debug")
    lazy val debugPort = SettingKey[Int]("appengine-debug-port")
    lazy val includeLibUser = SettingKey[Boolean]("appengine-include-lib-user")
    lazy val persistenceApi = SettingKey[String]("appengine-persistence-api", "Name of the API we are enhancing for: JDO, JPA.")
  }
  private val gae = AppengineKeys

  object AppEngine {
    // see https://github.com/jberkel/android-plugin/blob/master/src/main/scala/AndroidHelpers.scala
    def appcfgTask(action: String, outputFile: Option[String] = None): Initialize[InputTask[Unit]] =
      Def.inputTask {
        import complete.DefaultParsers._
        val cmdOptions: Seq[String] = spaceDelimited("<arg>").parsed
        webappPrepare.value
        appcfgTaskCmd(gae.appcfgPath.value, cmdOptions,
          Seq(action, (target in webappPrepare).value.getAbsolutePath)
            ++ outputFile.toSeq, streams.value)
      }

    def appcfgBackendTask(action: String,
      reqParam: Boolean = false): Initialize[InputTask[Unit]] =
      Def.inputTask {
        import complete.DefaultParsers._
        val input: Seq[String] = spaceDelimited("<arg>").parsed
        val (opts, args) = input.partition(_.startsWith("--"))
        if (reqParam && args.isEmpty) {
          sys.error("error executing appcfg: required parameter missing")
        }
        webappPrepare.value
        appcfgTaskCmd(gae.appcfgPath.value, opts,
          Seq("backends", (target in webappPrepare).value.getAbsolutePath, action)
            ++ args, streams.value)
      }

    def appcfgTaskCmd(appcfgPath: sbt.File, args: Seq[String],
      params: Seq[String], s: TaskStreams) = {
      val appcfg: Seq[String] = Seq(appcfgPath.absolutePath.toString) ++ args ++ params
      s.log.debug(appcfg.mkString(" "))
      val out = new StringBuffer
      val exit = Process(appcfg).!<

      if (exit != 0) {
        s.log.error(out.toString)
        sys.error("error executing appcfg")
      } else s.log.info(out.toString)
    }

    def isWindows = System.getProperty("os.name").startsWith("Windows")
    def osBatchSuffix = if (isWindows) ".cmd" else ".sh"
  }

  var devServerProc: Option[Process] = None

  lazy val baseAppengineSettings: Seq[Def.Setting[_]] = Seq(
    // this is classpath during compile
    //unmanagedClasspath ++= gae.classpath.value,
    // this is classpath included into WEB-INF/lib
    // https://developers.google.com/appengine/docs/java/tools/ant
    // "All of these JARs are in the SDK's lib/user/ directory."
    //unmanagedClasspath in DefaultClasspathConf ++= unmanagedClasspath.value,

    gae.requestLogs := AppEngine.appcfgTask("request_logs", outputFile = Some("request.log")).evaluated,
    gae.rollback := AppEngine.appcfgTask("rollback").evaluated,
    gae.deploy := AppEngine.appcfgTask("update").evaluated,
    gae.deployIndexes := AppEngine.appcfgTask("update_indexes").evaluated,
    gae.deployCron := AppEngine.appcfgTask("update_cron").evaluated,
    gae.deployQueues := AppEngine.appcfgTask("update_queues").evaluated,
    gae.deployDos := AppEngine.appcfgTask("update_dos").evaluated,
    gae.cronInfo := AppEngine.appcfgTask("cron_info").evaluated,

    gae.deployBackends := AppEngine.appcfgBackendTask("update").evaluated,
    gae.configBackends := AppEngine.appcfgBackendTask("configure").evaluated,
    gae.rollbackBackend := AppEngine.appcfgBackendTask("rollback", true).evaluated,
    gae.startBackend := AppEngine.appcfgBackendTask("start", true).evaluated,
    gae.stopBackend := AppEngine.appcfgBackendTask("stop", true).evaluated,
    gae.deleteBackend := AppEngine.appcfgBackendTask("delete", true).evaluated,

    //TODO: Exit code on stop
    //TODO: Change to command? Or o/wise get rid of var
    //TODO: Remove dependencies to revolver in build.sbt
    //TODO: Clean up keys which are not needed anymore.
    gae.devServer := {
      val args = spaceDelimited("<arg>").parsed

      //TODO Make this a setting?
      val arguments = Seq("-ea",
        "-cp", gae.apiToolsPath.value.getAbsolutePath(),
        "com.google.appengine.tools.KickStart",
        "com.google.appengine.tools.development.DevAppServerMain",
        (target in webappPrepare).value.getAbsolutePath()) ++ args

      val forkOptions = new ForkOptions(javaHome = javaHome.value,
        outputStrategy = outputStrategy.value,
        bootJars = Seq(),
        workingDirectory = Some(baseDirectory.value),
        runJVMOptions = Seq(),
        connectInput = false,
        envVars = Map())

      devServerProc = Some(Fork.java.fork(forkOptions, arguments))
      devServerProc.get
    },
    gae.stopDevServer := devServerProc.map(x => x.destroy()),

    gae.apiToolsJar := "appengine-tools-api.jar",
    gae.sdkVersion := SdkResolver.appengineVersion.value,
    gae.sdkPath := SdkResolver.buildAppengineSdkPath.value,

    gae.includeLibUser := true,
    // this controls appengine classpath, which is used in unmanagedClasspath
    gae.classpath := {
      if (gae.includeLibUser.value) (gae.libUserPath.value ** "*.jar").classpath
      else Nil
    },

    gae.apiJarName := ((gae.sdkVersion) { (v) => "appengine-api-1.0-sdk-" + v + ".jar" }).value,
    gae.apiLabsJarName := ((gae.sdkVersion) { (v) => "appengine-api-labs-" + v + ".jar" }).value,

    gae.binPath := new File(gae.sdkPath.value, "bin"),
    gae.libPath := new File(gae.sdkPath.value, "lib"),
    gae.libUserPath := new File(gae.libPath.value, "user"),
    gae.libImplPath := new File(gae.libPath.value, "impl"),
    gae.apiJarPath := { gae.libUserPath.value / gae.apiJarName.value },
    gae.apiToolsPath := { gae.libPath.value / gae.apiToolsJar.value },
    gae.appcfgName := "appcfg" + AppEngine.osBatchSuffix,
    gae.appcfgPath := {
      val path = gae.binPath.value / gae.appcfgName.value
      //Also need to set run_java as executable
      (gae.binPath.value / ("run_java" + AppEngine.osBatchSuffix)).setExecutable(true)
      path.setExecutable(true)
      path
    },
    gae.overridePath := gae.libPath.value / "override",
    gae.overridesJarPath := { gae.overridePath.value / "appengine-dev-jdk-overrides.jar" },
    gae.agentJarPath := { gae.libPath.value / "agent" / "appengine-agent.jar" })

  override lazy val projectSettings = appengineSettings
  lazy val appengineSettings: Seq[Def.Setting[_]] =
    WarPlugin.projectSettings ++
      inConfig(Compile)(baseAppengineSettings) ++
      inConfig(Test)(Seq(
        //unmanagedClasspath ++= gae.classpath.value,
        gae.classpath := {
          val impljars = ((gae.libImplPath in Compile).value * "*.jar").get
          val testingjars = ((gae.libPath in Compile).value / "testing" * "*.jar").get
          (gae.classpath in Compile).value ++ Attributed.blankSeq(impljars ++ testingjars)
        }))
}
