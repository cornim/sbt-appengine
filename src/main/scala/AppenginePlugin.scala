package sbtappengine

import sbt._
import sbt.Attributed.data
import complete.DefaultParsers.spaceDelimited
import com.earldouglas.xwp.WarPlugin
import com.earldouglas.xwp.WebappPlugin.autoImport.webappPrepare

//TODO explicit imports
//TODO use autoImport
//TODO override trigger
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
    lazy val stopDevServer = TaskKey[Option[Int]]("gae-stop-dev-server", "Stop development server.")
    lazy val appcfgPath = TaskKey[File]("appengine-appcfg-path")

    lazy val apiToolsPath = TaskKey[File]("appengine-api-tools-path", "Path of the development startup executable jar 'appengine-api-tools.jar'.")
    lazy val sdkVersion = SettingKey[String]("appengine-sdk-version")
    lazy val sdkPath = TaskKey[File]("appengine-sdk-path", "Sets sdk path and retrives sdk if necessary.")

    lazy val forkOptions = SettingKey[ForkOptions]("appengine-fork-options", "Options for forking dev server process")
    lazy val localDbPath = SettingKey[Option[File]]("appengine-local-db-path", "Path of local db for dev server.")
    lazy val devServerArgs = SettingKey[Seq[String]]("appengine-dev-server-args", "Additional arguments for starting the development server.")
    lazy val debug = SettingKey[Boolean]("appengine-debug", "Set debug mode of dev server on/off.")
    lazy val debugPort = SettingKey[Int]("appengine-debug-port", "Dev server debug port")
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

  def ensureIsExecutable(file: File) = {
    if (!file.canExecute()) file.setExecutable(true)
    file
  }

  var devServerProc: Option[Process] = None

  lazy val baseAppengineSettings: Seq[Def.Setting[_]] = Seq(

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

    gae.localDbPath := None,
    gae.devServerArgs := Seq(),
    gae.forkOptions := new ForkOptions(javaHome = javaHome.value,
      outputStrategy = outputStrategy.value,
      bootJars = Seq(),
      workingDirectory = Some(baseDirectory.value),
      runJVMOptions = Seq(),
      connectInput = false,
      envVars = Map()),
    //TODO: Change to command? Or o/wise get rid of var
    gae.devServer := {
      val args = spaceDelimited("<arg>").parsed

      val arguments = Seq("-ea",
        "-cp", gae.apiToolsPath.value.getAbsolutePath()) ++
        gae.localDbPath.value.map(_.getAbsolutePath()) ++
        Seq("com.google.appengine.tools.KickStart",
          "com.google.appengine.tools.development.DevAppServerMain",
          (target in webappPrepare).value.getAbsolutePath()) ++
          gae.devServerArgs.value ++ args

      devServerProc = Some(Fork.java.fork(gae.forkOptions.value, arguments))
      devServerProc.get
    },
    gae.stopDevServer := {
      devServerProc.map(_.destroy())
      val ret = devServerProc.map(_.exitValue())
      devServerProc = None
      ret
    },

    gae.sdkVersion := SdkResolver.appengineVersion.value,
    gae.sdkPath := SdkResolver.buildAppengineSdkPath.value,

    gae.apiToolsPath := gae.sdkPath.value / "lib" / "appengine-tools-api.jar",
    gae.appcfgPath := {
      //Setting run_java to executable is needed because appcfg calls run_java
      ensureIsExecutable(gae.sdkPath.value / "bin" / ("run_java" + AppEngine.osBatchSuffix))
      ensureIsExecutable(gae.sdkPath.value / "bin" / ("appcfg" + AppEngine.osBatchSuffix))
    })

  override lazy val projectSettings = appengineSettings
  lazy val appengineSettings: Seq[Def.Setting[_]] =
    WarPlugin.projectSettings ++
      inConfig(Compile)(baseAppengineSettings)
}
