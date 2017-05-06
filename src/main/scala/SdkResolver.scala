package sbtappengine

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilenameFilter
import java.io.IOException
import java.util.zip.ZipFile

import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.retrieve.RetrieveOptions

import sbt.Def
import sbt.Def.macroValueI
import sbt.Def.macroValueIT
import sbt.IvySbt
import sbt.Keys.TaskStreams
import sbt.Keys.baseDirectory
import sbt.Keys.ivySbt
import sbt.Keys.libraryDependencies
import sbt.Keys.streams

object SdkResolver {

  val appengineVersion = Def.setting {
    val searchStr = "appengine-api-1.0-sdk"
    val appEngineVersionStringOpt = libraryDependencies.value
      .filter(_.name == searchStr).headOption
      .map(_.revision)
    if (appEngineVersionStringOpt.isDefined) appEngineVersionStringOpt.get
    else sys.error("You need to add " + searchStr + " to your libraryDependencies.")
  }

  val buildAppengineSdkPath = Def.task {
    val downloadDir = retriveDependency(appengineVersion.value,
      ivySbt.value, baseDirectory.value, streams.value)
    unzipSdk(downloadDir, appengineVersion.value)
  }

  def retriveDependency(version: String, ivySbt: IvySbt, baseDir: File, s: TaskStreams) = {
    val groupId = "com.google.appengine"
    val artifactId = "appengine-java-sdk"

    val ro = new ResolveOptions();
    ro.setTransitive(true)
    ro.setDownload(true)

    // 1st create an ivy module (this always(!) has a "default" configuration already)
    val md = DefaultModuleDescriptor.newDefaultInstance(
      // give it some related name (so it can be cached)
      ModuleRevisionId.newInstance(
        groupId,
        artifactId + "-envelope",
        version));

    // 2. add dependencies for what we are really looking for
    val ri = ModuleRevisionId.newInstance(
      groupId,
      artifactId,
      version);
    // don't go transitive here, if you want the single artifact
    val dd = new DefaultDependencyDescriptor(md, ri, false, false, false);

    // map to master to just get the code jar. See generated ivy module xmls from maven repo
    // on how configurations are mapped into ivy. Or check 
    // e.g. http://lightguard-jp.blogspot.de/2009/04/ivy-configurations-when-pulling-from.html
    dd.addDependencyConfiguration("default", "master");
    md.addDependency(dd);

    ivySbt.withIvy(s.log) { ivy: Ivy =>
      {
        // now resolve
        val rr = ivy.resolve(md, ro);
        if (rr.hasError()) sys.error(rr.getAllProblemMessages().toString())

        // Step 2: retrieve
        val m = rr.getModuleDescriptor();

        val out = new File(baseDir, "appengine-java-sdk")

        val retrieveExitCode = ivy.retrieve(
          m.getModuleRevisionId(),
          out.getAbsolutePath() + "/[artifact](-[classifier]).[ext]",
          new RetrieveOptions()
            // this is from the envelop module
            .setConfs(Array[String] { "default" }));
        out
      }
    }
  }

  def unzipSdk(sdkRepoDir: File, sdkVersion: String): File = {
    val sdkBaseDir = new File(sdkRepoDir, sdkVersion)
    val sdkArchiveOpt = sdkRepoDir.listFiles(new FilenameFilter() {
      def accept(dir: File, filename: String) = filename.endsWith(".zip")
    }).headOption

    //Exit with error if we can't find zip file
    if (sdkArchiveOpt.isEmpty) sys.error("Could not find zip file to unpack in "
      + sdkRepoDir.getAbsolutePath())

    val sdkArchive = sdkArchiveOpt.get

    if (sdkBaseDir.exists() && !sdkBaseDir.isDirectory()) {
      sys.error("Could not unpack the SDK because there is an unexpected file at "
        + sdkBaseDir + " which conflicts with where we plan to unpack the SDK.");
    }

    if (!sdkBaseDir.exists()) sdkBaseDir.mkdirs();

    // While processing the zip archive, if we find an initial entry that is a directory,
    // and all entries are a child
    // of this directory, then we append this to the sdkBaseDir we return.
    var sdkBaseDirSuffix: String = null

    try {
      val sdkZipArchive = new ZipFile(sdkArchive);
      var zipEntries = sdkZipArchive.entries();

      if (!zipEntries.hasMoreElements()) {
        sys.error("The SDK zip archive appears corrupted.  There are no entries in the zip index.");
      }

      val firstEntry = zipEntries.nextElement();
      if (firstEntry.isDirectory()) {
        sdkBaseDirSuffix = firstEntry.getName();
      } else {
        //Reinitialize entries
        zipEntries = sdkZipArchive.entries();
      }

      while (zipEntries.hasMoreElements()) {
        val zipEntry = zipEntries.nextElement();

        if (!zipEntry.isDirectory()) {
          val zipEntryDestination = new File(sdkBaseDir, zipEntry.getName());

          if (!zipEntry.getName().startsWith(sdkBaseDirSuffix)) {
            //We found an entry that doesn't use this initial base directory, oh well, just set it to null.
            sdkBaseDirSuffix = null;
          }

          if (!zipEntryDestination.exists()) {
            createParentDirs(zipEntryDestination)
            extractFile(new BufferedInputStream(sdkZipArchive.getInputStream(zipEntry)),
              zipEntryDestination)
          }
        }
      }

    } catch {
      case e: IOException => sys.error("Could not open SDK zip archive.\n" + e);
    } finally {
      //sdkArchive.delete()
    }

    if (sdkBaseDirSuffix == null) sdkBaseDir
    else new File(sdkBaseDir, sdkBaseDirSuffix)
  }

  def createParentDirs(file: File): Unit = {
    if (file == null) return
    val parent = file.getCanonicalFile().getParentFile();
    if (parent == null) {
      /*
       * The given directory is a filesystem root. All zero of its ancestors
       * exist. This doesn't mean that the root itself exists -- consider x:\ on
       * a Windows machine without such a drive -- or even that the caller can
       * create it, but this method makes no such guarantees even for non-root
       * files.
       */
      return
    }
    parent.mkdirs();
    if (!parent.isDirectory()) sys.error("Unable to create parent directories of " + file)
  }

  def extractFile(is: BufferedInputStream, destFile: File): Unit = {
    val BUFFER = 4096
    var currentByte = 0
    // establish buffer for writing file
    val data = new Array[Byte](BUFFER)

    // write the current file to disk
    val fos = new FileOutputStream(destFile);
    val dest = new BufferedOutputStream(fos,
      BUFFER);

    // read and write until last byte is encountered
    currentByte = is.read(data, 0, BUFFER)
    while (currentByte != -1) {
      dest.write(data, 0, currentByte)
      currentByte = is.read(data, 0, BUFFER)
    }
    dest.flush();
    dest.close();
    is.close();
  }
}