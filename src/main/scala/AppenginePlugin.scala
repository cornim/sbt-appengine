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
    lazy val gaeRequestLogs = InputKey[Unit]("gae-request-logs", "Write request logs in Apache common log format.")
    lazy val gaeRollback = InputKey[Unit]("gae-rollback", "Rollback an in-progress update.")
    lazy val gaeDeploy = InputKey[Unit]("gae-deploy", "Create or update an app version.")
    lazy val gaeDeployBackends = InputKey[Unit]("gae-deploy-backends", "Update the specified backend or all backends.")
    lazy val gaeRollbackBackend = InputKey[Unit]("gae-rollback-backend", "Roll back a previously in-progress update.")
    lazy val gaeConfigBackends = InputKey[Unit]("gae-config-backends", "Configure the specified backend.")
    lazy val gaeStartBackend = InputKey[Unit]("gae-start-backend", "Start the specified backend.")
    lazy val gaeStopBackend = InputKey[Unit]("gae-stop-backend", "Stop the specified backend.")
    lazy val gaeDeleteBackend = InputKey[Unit]("gae-delete-backend", "Delete the specified backend.")
    lazy val gaeDeployIndexes = InputKey[Unit]("gae-deploy-indexes", "Update application indexes.")
    lazy val gaeDeployCron = InputKey[Unit]("gae-deploy-cron", "Update application cron jobs.")
    lazy val gaeDeployQueues = InputKey[Unit]("gae-deploy-queues", "Update application task queue definitions.")
    lazy val gaeDeployDos = InputKey[Unit]("gae-deploy-dos", "Update application DoS protection configuration.")
    lazy val gaeCronInfo = InputKey[Unit]("gae-cron-info", "Displays times for the next several runs of each cron job.")
    lazy val gaeDevServer = InputKey[Process]("gae-dev-server", "Run application through development server.")
    lazy val gaeDevServerStop = TaskKey[Option[Int]]("gae-dev-server-stop", "Stop development server.")
    lazy val gaeAppcfgPath = TaskKey[File]("gae-appcfg-path", "Appcfg path")

    lazy val gaeApiToolsPath = TaskKey[File]("gae-api-tools-path", "Path of the development startup executable jar 'appengine-api-tools.jar'.")
    lazy val gaeSdkVersion = SettingKey[String]("gae-sdk-version", "Version of the google appengine sdk.")
    lazy val gaeSdkPath = TaskKey[File]("gae-sdk-path", "Sets sdk path and retrives sdk if necessary.")

    lazy val gaeForkOptions = SettingKey[ForkOptions]("gae-fork-options", "Options for forking dev server process")
    lazy val gaeLocalDbPath = SettingKey[Option[File]]("gae-local-db-path", "Path of local db for dev server.")
    lazy val gaeDevServerArgs = SettingKey[Seq[String]]("gae-dev-server-args", "Additional arguments for starting the development server.")
    
    lazy val gaeDebug = SettingKey[Boolean]("gae-debug", "Set debug mode of dev server on/off.")
    lazy val gaeDebugPort = SettingKey[Int]("gae-debug-port", "Dev server debug port")
  }
  import autoImport._

  // see https://github.com/jberkel/android-plugin/blob/master/src/main/scala/AndroidHelpers.scala
  def appcfgTask(action: String, outputFile: Option[String] = None): Initialize[InputTask[Unit]] =
    Def.inputTask {
      val cmdOptions: Seq[String] = spaceDelimited("<arg>").parsed
      webappPrepare.value
      appcfgTaskCmd(gaeAppcfgPath.value, cmdOptions,
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
      appcfgTaskCmd(gaeAppcfgPath.value, opts,
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
    gaeRequestLogs := appcfgTask("request_logs", outputFile = Some("request.log")).evaluated,
    gaeRollback := appcfgTask("rollback").evaluated,
    gaeDeploy := appcfgTask("update").evaluated,
    gaeDeployIndexes := appcfgTask("update_indexes").evaluated,
    gaeDeployCron := appcfgTask("update_cron").evaluated,
    gaeDeployQueues := appcfgTask("update_queues").evaluated,
    gaeDeployDos := appcfgTask("update_dos").evaluated,
    gaeCronInfo := appcfgTask("cron_info").evaluated,

    gaeDeployBackends := appcfgBackendTask("update").evaluated,
    gaeConfigBackends := appcfgBackendTask("configure").evaluated,
    gaeRollbackBackend := appcfgBackendTask("rollback", true).evaluated,
    gaeStartBackend := appcfgBackendTask("start", true).evaluated,
    gaeStopBackend := appcfgBackendTask("stop", true).evaluated,
    gaeDeleteBackend := appcfgBackendTask("delete", true).evaluated)

  var devServerProc: Option[Process] = None

  lazy val appengineDevServerSettings = Seq(
    gaeLocalDbPath := None,
    gaeDevServerArgs := Seq(),
    gaeDebug := true,
    gaeDebugPort := 8888,
    gaeForkOptions := new ForkOptions(javaHome = javaHome.value,
      outputStrategy = outputStrategy.value,
      bootJars = Seq(),
      workingDirectory = Some(baseDirectory.value),
      runJVMOptions = Seq(),
      connectInput = false,
      envVars = Map()),

    gaeDevServer := {
      val args = spaceDelimited("<arg>").parsed
      
      val dbOption = if (gaeLocalDbPath.value.isDefined){
           Seq("--jvm_flag=-Ddatastore.backing_store=" + gaeLocalDbPath.value.get.getAbsolutePath())
      } else Seq[String]()
      
      val debugOption = if (gaeDebug.value) {
        Seq("--jvm_flag=-Xdebug", "--jvm_flag=-Xrunjdwp:transport=dt_socket,address=" +
            gaeDebugPort.value + ",server=y,suspend=y")
      } else Seq[String]()

      webappPrepare.value

      val arguments = Seq("-ea",
        "-cp", gaeApiToolsPath.value.getAbsolutePath()) ++
        Seq("com.google.appengine.tools.KickStart",
          "com.google.appengine.tools.development.DevAppServerMain") ++
          gaeDevServerArgs.value ++ dbOption ++ debugOption ++ args ++
          Seq((target in webappPrepare).value.getAbsolutePath())

      devServerProc = Some(Fork.java.fork(gaeForkOptions.value, arguments))
      devServerProc.get
    },
    gaeDevServerStop := {
      devServerProc.map(_.destroy())
      val ret = devServerProc.map(_.exitValue())
      devServerProc = None
      if (ret.isDefined) streams.value.log("Dev server exit code: " + ret.get)
      ret
    },

    gaeSdkVersion := SdkResolver.appengineVersion.value,
    gaeSdkPath := SdkResolver.buildAppengineSdkPath.value,

    gaeApiToolsPath := gaeSdkPath.value / "lib" / "appengine-tools-api.jar",
    gaeAppcfgPath := {
      //Setting run_java to executable is needed because appcfg calls run_java
      ensureIsExecutable(gaeSdkPath.value / "bin" / ("run_java" + osBatchSuffix))
      ensureIsExecutable(gaeSdkPath.value / "bin" / ("appcfg" + osBatchSuffix))
    })

  override def projectSettings =
    appengineAppCfgSettings ++ appengineDevServerSettings
}
