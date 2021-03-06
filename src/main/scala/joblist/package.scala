import java.io.PrintWriter
import java.nio.file.{FileVisitOption, Files, Path}

import better.files._
import joblist.local.LocalScheduler
import joblist.lsf.LsfScheduler
import joblist.slurm.SlurmScheduler
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import scalautils.StringUtils.ImplStringUtils
import scalautils.{Bash, IOUtils}

/**
  * Some generic utilities used to manipulate, create and manage joblists.
  *
  * @author Holger Brandl
  */
package object joblist {

  implicit class ImplJobListUtils(jl: JobList) {

    // simple convenience wrapper
    def createHtmlReport() = new JobReport(jl).createHtmlReport()
  }


  val DEFAULT_JL = ".jobs"


  //  To use single verbs you can use some provided shortcuts by adding this to your bash_profile
  //  ```
  //  eval "$(jl shortcuts)"
  //  ```
  @Deprecated
  def shortcuts() = {
    println(
      """
         # none yet
      """.alignLeft)
  }


  /** see https://github.com/holgerbrandl/joblist/milestones/v0.6
    * The feature is optional and could be removed if it turns out to be too confusing for the users
    */
  val rememberMeFile = ".last_jl"

  def updateLastJL(jl: JobList) = (jl.file.parent / rememberMeFile).write(jl.file.name)


  def getDefaultJlFile(wd: File = File(".")): File = {
    val fallbackFile: File = wd / ".jobs"

    val wdRemMeFile: File = wd / rememberMeFile
    if (wdRemMeFile.isRegularFile) {
      val restoredJl: File = File(wdRemMeFile.lines.head)
      if (restoredJl.isRegularFile) {
        // println(s"restored jl is $restoredJl")
        return restoredJl
      } else {
        wdRemMeFile.delete(true)
      }
    }

    fallbackFile
  }

  def guessScheduler(): JobScheduler = {

    if (sys.env.get("JL_FORCE_LOCAL").isDefined) {
      Console.err.println("Using local scheduler")
      return new LocalScheduler
    }

    if (isLSF) {
      return new LsfScheduler()
    }

    if (isSLURM) {
      return new SlurmScheduler()
    }

    new LocalScheduler()
  }


  def isSLURM: Boolean = {
    Bash.eval("which squeue").sout.nonEmpty
  }

  def isTravisCI: Boolean = sys.env.isDefinedAt("IS_TRAVIS_CI")


  def isLSF: Boolean = {
    Bash.eval("which bkill").sout.nonEmpty || Option(System.getenv("USE_FAKE_LSF")).isDefined
  }

  def isLocal = !isLSF && !isSLURM


  def whoAmI: String = Bash.eval("whoami").sout


  def getConfigRoots(jobs: List[Job]) = {

    // optionally (and by default) we should use apply the original job configurations for escalation and resubmission?
    def findRootJC(job: Job): Job = {
      job.resubOf match {
        case Some(rootJob) => findRootJC(rootJob)
        case None => job
      }
    }

    jobs.map(findRootJC)
  }


  //  private def changeWdOptional(wd: File): String = {
  //    if (wd != null && wd != File(".")) "cd " + wd.pathAsString + "; " else ""
  //  }
  case class RunInfo(jobId: Int, user: String, state: joblist.JobState.JobState,
                     scheduler: String,
                     queue: String,
                     //                  FromHost:String,
                     execHost: String,
                     jobName: String,
                     submitTime: DateTime,
                     //                  ProjName:String, CpuUsed:Int, Mem:Int, Swap:Int, Pids:List[Int],
                     startTime: DateTime, finishTime: DateTime,
                     // additional fields
                     exitCode: Int,
                     killCause: String = ""
                    ) {

  }


  // configure better-files to always follow links
  implicit val visitOptions: File.VisitOptions = File.VisitOptions.follow



  // @Deprecated Because fixed in latest not-yet-released better.files
  implicit class ImplFileUtils(file: File) {

    /** Workaround for https://github.com/pathikrit/better-files/issues/51 */
    @Deprecated
    def allLines = {
      Files.readAllLines(file.path).toList
    }


    // todo remove this once bf is providing by default
    def absolute = {
      File(file.path.toAbsolutePath.toString)
    }

//    /** An nfs-aware exists implementation inspired by http://stackoverflow.com/questions/3833127/alternative-to-file-exists-in-java . */
//    def refreshNfsStats = {
//      //    Bash.eval(s"ls ${resultFile}").exitCode==0
//      // by listing th parent directory meta-data is being refreshed.
//      file.parent.list
//      file
//    }


    /** Expose Path.resolve for simpler typing and less braces. */
    def resolve(childName: String) = file / childName


    /**
      * Util to glob from this file's path
      * @return Set of files that matched
      */
//    def glob_links(pattern: String, syntax: String = "glob")(implicit visitOptions: File.VisitOptions = File.VisitOptions.default) = {
    def glob_links(pattern: String, syntax: String = "glob")(implicit visitOptions: File.VisitOptions ) = {
      val matcher = file.fileSystem.getPathMatcher(s"$syntax:$pattern")

      java.nio.file.Files.walk(file.path, visitOptions:_*).iterator().toSeq.
        filter(testPath => matcher.matches(file.relativize(testPath))).
          map(new File(_))
    }


    def saveAs: ((PrintWriter) => Unit) => Unit = IOUtils.saveAs(file.toJava)
  }

}
