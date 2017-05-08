package sbtappengine

import com.earldouglas.xwp.WarPlugin
import com.earldouglas.xwp.WebappPlugin.autoImport.webappPrepare

import sbt.AutoPlugin
import sbt.Compile
import sbt.Def
import sbt.Def.Initialize
import sbt.Def.macroValueI
import sbt.Def.macroValueIInT
import sbt.Def.macroValueIT
import sbt.File
import sbt.Fork
import sbt.ForkOptions
import sbt.InputKey
import sbt.InputTask
import sbt.Keys.TaskStreams
import sbt.Keys.baseDirectory
import sbt.Keys.javaHome
import sbt.Keys.outputStrategy
import sbt.Keys.streams
import sbt.Keys.target
import sbt.Process
import sbt.SettingKey
import sbt.TaskKey
import sbt.complete.DefaultParsers.spaceDelimited
import sbt.inConfig
import sbt.parserToInput
import sbt.richFile

object AppenginePlugin extends AutoPlugin {

  override def requires = WarPlugin

  override def trigger = allRequirements

  object autoImport {
    lazy val requestLogs = InputKey[Unit]("gae-request-logs", "Write request logs in Apache common log format.")
    lazy val rollback = InputKey[Unit]("gae-rollback", "Rollback an in-progress update.")
    lazy val deploy = InputKey[Unit]("gae-deploy", "Create or update an app version.")
    lazy val deployBackends = InputKey[Unit]("gae-deploy-backends", "Update the specified backend or all backends.")
    lazy val rollbackBackend = InputKey[Unit]("gae-rollback-backends", "Roll back a previously in-progress update.")
    lazy val configBackends = InputKey[Unit]("gae-config-backends", "Configure the specified backend.")
    lazy val startBackend = InputKey[Unit]("gae-start-backend", "Start the specified backend.")
    lazy val stopBackend = InputKey[Unit]("gae-stop-backend", "Stop the specified backend.")
    lazy val deleteBackend = InputKey[Unit]("gae-delete-backend", "Delete the specified backend.")
    lazy val deployIndexes = InputKey[Unit]("gae-deploy-indexes", "Update application indexes.")
    lazy val deployCron = InputKey[Unit]("gae-deploy-cron", "Update application cron jobs.")
    lazy val deployQueues = InputKey[Unit]("gae-deploy-queues", "Update application task queue definitions.")
    lazy val deployDos = InputKey[Unit]("gae-deploy-dos", "Update application DoS protection configuration.")
    lazy val cronInfo = InputKey[Unit]("gae-cron-info", "Displays times for the next several runs of each cron job.")
    lazy val devServer = InputKey[Process]("gae-dev-server", "Run application through development server.")
    lazy val stopDevServer = TaskKey[Option[Int]]("gae-stop-dev-server", "Stop development server.")
    lazy val appcfgPath = TaskKey[File]("gae-appcfg-path")

    lazy val apiToolsPath = TaskKey[File]("gae-api-tools-path", "Path of the development startup executable jar 'appengine-api-tools.jar'.")
    lazy val sdkVersion = SettingKey[String]("gae-sdk-version")
    lazy val sdkPath = TaskKey[File]("gae-sdk-path", "Sets sdk path and retrives sdk if necessary.")

    lazy val forkOptions = SettingKey[ForkOptions]("gae-fork-options", "Options for forking dev server process")
    lazy val localDbPath = SettingKey[Option[File]]("gae-local-db-path", "Path of local db for dev server.")
    lazy val devServerArgs = SettingKey[Seq[String]]("gae-dev-server-args", "Additional arguments for starting the development server.")
    lazy val debug = SettingKey[Boolean]("gae-debug", "Set debug mode of dev server on/off.")
    lazy val debugPort = SettingKey[Int]("gae-debug-port", "Dev server debug port")
  }
  import autoImport._

  // see https://github.com/jberkel/android-plugin/blob/master/src/main/scala/AndroidHelpers.scala
  def appcfgTask(action: String, outputFile: Option[String] = None): Initialize[InputTask[Unit]] =
    Def.inputTask {
      val cmdOptions: Seq[String] = spaceDelimited("<arg>").parsed
      webappPrepare.value
      appcfgTaskCmd(appcfgPath.value, cmdOptions,
        Seq(action, (target in webappPrepare).value.getAbsolutePath)
          ++ outputFile.toSeq, streams.value)
    }

  def appcfgBackendTask(action: String,
    reqParam: Boolean = false): Initialize[InputTask[Unit]] =
    Def.inputTask {
      val input: Seq[String] = spaceDelimited("<arg>").parsed
      val (opts, args) = input.partition(_.startsWith("--"))
      if (reqParam && args.isEmpty) {
        sys.error("error executing appcfg: required parameter missing")
      }
      webappPrepare.value
      appcfgTaskCmd(appcfgPath.value, opts,
        Seq("backends", (target in webappPrepare).value.getAbsolutePath, action)
          ++ args, streams.value)
    }

  def appcfgTaskCmd(appcfgPath: File, args: Seq[String],
    params: Seq[String], s: TaskStreams) = {
    val appcfg: Seq[String] = Seq(appcfgPath.getAbsolutePath()) ++ args ++ params
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

  def ensureIsExecutable(file: File) = {
    if (!file.canExecute()) file.setExecutable(true)
    file
  }

  lazy val appengineAppCfgSettings: Seq[Def.Setting[_]] = Seq(
    requestLogs := appcfgTask("request_logs", outputFile = Some("request.log")).evaluated,
    rollback := appcfgTask("rollback").evaluated,
    deploy := appcfgTask("update").evaluated,
    deployIndexes := appcfgTask("update_indexes").evaluated,
    deployCron := appcfgTask("update_cron").evaluated,
    deployQueues := appcfgTask("update_queues").evaluated,
    deployDos := appcfgTask("update_dos").evaluated,
    cronInfo := appcfgTask("cron_info").evaluated,

    deployBackends := appcfgBackendTask("update").evaluated,
    configBackends := appcfgBackendTask("configure").evaluated,
    rollbackBackend := appcfgBackendTask("rollback", true).evaluated,
    startBackend := appcfgBackendTask("start", true).evaluated,
    stopBackend := appcfgBackendTask("stop", true).evaluated,
    deleteBackend := appcfgBackendTask("delete", true).evaluated)

  var devServerProc: Option[Process] = None

  lazy val appengineDevServerSettings = Seq(
    localDbPath := None,
    devServerArgs := Seq(),
    forkOptions := new ForkOptions(javaHome = javaHome.value,
      outputStrategy = outputStrategy.value,
      bootJars = Seq(),
      workingDirectory = Some(baseDirectory.value),
      runJVMOptions = Seq(),
      connectInput = false,
      envVars = Map()),

    devServer := {
      val args = spaceDelimited("<arg>").parsed

      val arguments = Seq("-ea",
        "-cp", apiToolsPath.value.getAbsolutePath()) ++
        localDbPath.value.map(_.getAbsolutePath()) ++
        Seq("com.google.appengine.tools.KickStart",
          "com.google.appengine.tools.development.DevAppServerMain",
          (target in webappPrepare).value.getAbsolutePath()) ++
          devServerArgs.value ++ args

      devServerProc = Some(Fork.java.fork(forkOptions.value, arguments))
      devServerProc.get
    },
    stopDevServer := {
      devServerProc.map(_.destroy())
      val ret = devServerProc.map(_.exitValue())
      devServerProc = None
      if (ret.isDefined) streams.value.log("Dev server exit code: " + ret.get)
      ret
    },

    sdkVersion := SdkResolver.appengineVersion.value,
    sdkPath := SdkResolver.buildAppengineSdkPath.value,

    apiToolsPath := sdkPath.value / "lib" / "appengine-tools-api.jar",
    appcfgPath := {
      //Setting run_java to executable is needed because appcfg calls run_java
      ensureIsExecutable(sdkPath.value / "bin" / ("run_java" + osBatchSuffix))
      ensureIsExecutable(sdkPath.value / "bin" / ("appcfg" + osBatchSuffix))
    })

  override def projectSettings =
    appengineAppCfgSettings ++ appengineDevServerSettings
}
