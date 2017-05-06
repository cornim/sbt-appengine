package sbtappengine

import sbt._
import com.earldouglas.xwp.WarPlugin
import com.earldouglas.xwp.WebappPlugin.autoImport.webappPrepare

object AppenginePlugin extends AutoPlugin {
  override def requires = WarPlugin

  import Keys._
  import Def.Initialize
  import spray.revolver
  import revolver.Actions._
  import revolver.Utilities._

  object AppengineKeys extends revolver.RevolverKeys {
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
    lazy val devServer = InputKey[revolver.AppProcess]("appengine-dev-server", "Run application through development server.")
    lazy val stopDevServer = TaskKey[Unit]("appengine-stop-dev-server", "Stop development server.")
    lazy val enhance = TaskKey[Unit]("appengine-enhance", "Execute ORM enhancement.")
    lazy val enhanceCheck = TaskKey[Unit]("appengine-enhance-check", "Just check the classes for enhancement status.")

    lazy val onStartHooks = SettingKey[Seq[() => Unit]]("appengine-on-start-hooks")
    lazy val onStopHooks = SettingKey[Seq[() => Unit]]("appengine-on-stop-hooks")
    lazy val apiToolsJar = SettingKey[String]("appengine-api-tools-jar", "Name of the development startup executable jar.")
    lazy val apiToolsPath = TaskKey[File]("appengine-api-tools-path", "Path of the development startup executable jar.")
    lazy val sdkVersion = SettingKey[String]("appengine-sdk-version")
    lazy val sdkPath = TaskKey[File]("appengine-sdk-path", "Sets sdk path and retrives sdk if necessary.")
    lazy val classpath = TaskKey[Classpath]("appengine-classpath")
    lazy val apiJarName = SettingKey[String]("appengine-api-jar-name")
    lazy val apiLabsJarName = SettingKey[String]("appengine-api-labs-jar-name")
    lazy val jsr107CacheJarName = SettingKey[String]("appengine-jsr107-cache-jar-name")
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

    // see https://github.com/spray/sbt-revolver/blob/master/src/main/scala/spray/revolver/Actions.scala#L26
    def restartDevServer(streams: TaskStreams, logTag: String, project: ProjectRef, options: ForkOptions, mainClass: Option[String],
      cp: Classpath, args: Seq[String], startConfig: ExtraCmdLineOptions, war: File,
      onStart: Seq[() => Unit], onStop: Seq[() => Unit]): revolver.AppProcess = {
      if (revolverState.getProcess(project).exists(_.isRunning)) {
        colorLogger(streams.log).info("[YELLOW]Stopping dev server ...")
        stopAppWithStreams(streams, project)
        onStop foreach { _.apply() }
      }
      startDevServer(streams, logTag, project, options, mainClass, cp, args, startConfig, onStart)
    }
    // see https://github.com/spray/sbt-revolver/blob/master/src/main/scala/spray/revolver/Actions.scala#L32
    def startDevServer(streams: TaskStreams, logTag: String, project: ProjectRef,
      options: ForkOptions, mainClass: Option[String],
      cp: Classpath, args: Seq[String], startConfig: ExtraCmdLineOptions,
      onStart: Seq[() => Unit]): revolver.AppProcess = {

      assert(!revolverState.getProcess(project).exists(_.isRunning))

      val color = updateStateAndGet(_.takeColor)
      val logger = new revolver.SysoutLogger(logTag, color, streams.log.ansiCodesSupported)
      colorLogger(streams.log).info("[YELLOW]Starting dev server in the background ...")
      onStart foreach { _.apply() }
      //streams.log.info(cp.toString() + "\n" + options + "\n" + startConfig.jvmArgs
      //  + "\n" + mainClass.get + "\n" + startConfig.startArgs + "\n" + args)
      val appProcess = revolver.AppProcess(project, color, logger) {
        Fork.java.fork(options.javaHome,
          Seq("-cp", cp.map(_.data.absolutePath).mkString(System.getProperty("file.separator"))) ++
            options.runJVMOptions ++ startConfig.jvmArgs ++
            Seq(mainClass.get) ++
            startConfig.startArgs ++ args,
          options.workingDirectory, Map(), false, StdoutOutput)
      }
      registerAppProcess(project, appProcess)
      appProcess
    }
  }

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

    gae.devServer := {
      val args = startArgsParser.parsed
      val x = (products in Compile).value
      AppEngine.restartDevServer(streams.value, (gae.reLogTag in gae.devServer).value,
        thisProjectRef.value, (gae.reForkOptions in gae.devServer).value,
        (mainClass in gae.devServer).value, (fullClasspath in gae.devServer).value,
        (gae.reStartArgs in gae.devServer).value, args,
        (target in webappPrepare).value,
        (gae.onStartHooks in gae.devServer).value, (gae.onStopHooks in gae.devServer).value)
    },
    gae.reForkOptions in gae.devServer :=
      ForkOptions(javaHome.value, outputStrategy.value, scalaInstance.value.jars,
        Some((target in webappPrepare).value), (javaOptions in gae.devServer).value, false, Map()),
    gae.reLogTag in gae.devServer := "gae.devServer",
    mainClass in gae.devServer := Some("com.google.appengine.tools.development.DevAppServerMain"),
    fullClasspath in gae.devServer :=
      ((gae.apiToolsPath) map { (jar: File) => Seq(jar).classpath }).value,
    gae.localDbPath in gae.devServer := target.value / "local_db.bin",
    gae.reStartArgs in gae.devServer := Seq((target in webappPrepare).value.absolutePath),
    // http://thoughts.inphina.com/2010/06/24/remote-debugging-google-app-engine-application-on-eclipse/
    gae.debug in gae.devServer := true,
    gae.debugPort in gae.devServer := 1044,
    gae.onStartHooks in gae.devServer := Nil,
    gae.onStopHooks in gae.devServer := Nil,
    SbtCompat.impl.changeJavaOptions { (o, a, jr, ldb, d, dp) =>
      Seq("-ea", "-javaagent:" + a.getAbsolutePath, "-Xbootclasspath/p:" + o.getAbsolutePath,
        "-Ddatastore.backing_store=" + ldb.getAbsolutePath) ++
        Seq("-Djava.awt.headless=true") ++
        (if (d) Seq("-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=" + dp.toString) else Nil) ++
        createJRebelAgentOption(revolver.SysoutLogger, jr).toSeq
    },
    gae.stopDevServer := (gae.reStop map { identity }).value,

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
    gae.jsr107CacheJarName := ((gae.sdkVersion) { (v) => "appengine-jsr107cache-" + v + ".jar" }).value,

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
    gae.agentJarPath := { gae.libPath.value / "agent" / "appengine-agent.jar" },
    gae.emptyFile := file(""))

  override lazy val projectSettings = appengineSettings
  lazy val appengineSettings: Seq[Def.Setting[_]] =
    WarPlugin.projectSettings ++
      inConfig(Compile)(revolver.RevolverPlugin.Revolver.settings ++ baseAppengineSettings) ++
      inConfig(Test)(Seq(
        //unmanagedClasspath ++= gae.classpath.value,
        gae.classpath := {
          val impljars = ((gae.libImplPath in Compile).value * "*.jar").get
          val testingjars = ((gae.libPath in Compile).value / "testing" * "*.jar").get
          (gae.classpath in Compile).value ++ Attributed.blankSeq(impljars ++ testingjars)
        }))
}
