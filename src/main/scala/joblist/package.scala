import java.io.{BufferedWriter, FileWriter, PrintWriter}
import java.nio.file.Files
import java.text.DecimalFormat

import better.files.File
import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.converters.basic.AbstractSingleValueConverter
import com.thoughtworks.xstream.io.xml.DomDriver
import joblist.JobState.JobState
import joblist.local.LocalScheduler
import joblist.lsf.LsfScheduler
import joblist.slurm.SlurmScheduler
import org.joda.time.format.{DateTimeFormat, ISOPeriodFormat}
import org.joda.time.{DateTime, Duration, Seconds}

import scala.collection.JavaConversions._
import scalautils.{Bash, IOUtils}

/**
  * Some generic utilities used to manipulate, create and manage joblists.
  *
  * @author Holger Brandl
  */
package object joblist {

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


  def isLSF: Boolean = {
    Bash.eval("which bkill").sout.nonEmpty || Option(System.getenv("USE_FAKE_LSF")).isDefined
  }


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
  case class RunInfo(jobId: Int, user: String, state: joblist.JobState.JobState, queue: String,
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


  //noinspection AccessorLikeMethodIsUnit
  def toXml(something: Any, file: File) = {
    getXstream.toXML(something, new BufferedWriter(new FileWriter(file.toJava)))
  }


  def fromXml(file: File) = {
    getXstream.fromXML(file.toJava)
  }


  // see http://x-stream.github.io/converter-tutorial.html
  private class BetterFilerConverter extends AbstractSingleValueConverter {

    def canConvert(o: Class[_]): Boolean = {
      o == classOf[File]
    }


    def fromString(str: String): AnyRef = {
      File(str)
    }
  }


  // see http://x-stream.github.io/converter-tutorial.html
  private class JodaConverter extends AbstractSingleValueConverter {

    def canConvert(o: Class[_]): Boolean = {
      o == classOf[DateTime]
    }


    val formatter = DateTimeFormat.forPattern("dd-MM-yyyy HH:mm:ss")


    override def toString(obj: scala.Any): String = formatter.print(obj.asInstanceOf[DateTime])


    def fromString(str: String): AnyRef = {
      formatter.parseDateTime(str)
      //      new DateTime(new Date(str))
    }
  }


  def getXstream: XStream = {
    val xStream = new XStream(new DomDriver())

    xStream.registerConverter(new BetterFilerConverter())
    xStream.registerConverter(new JodaConverter())
    //    xStream.registerConverter(new JobStateConverter())

    xStream.alias("RunInfo", classOf[RunInfo])
    xStream.alias("JobState", classOf[JobState])

    xStream
  }


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


    /** Expose Path.resolve for simpler typing and less braces. */
    def resolve(childName: String) = file / childName


    def saveAs: ((PrintWriter) => Unit) => Unit = IOUtils.saveAs(file.toJava)
  }


  implicit class ImplJobListUtils(jl: JobList) {

    def exportStatistics() = {
      jl.requireListFile()

      val statsFile = File(jl.file + ".runinfo.log")

      statsFile.write(Seq("job_id", "job_name", "queue", "submit_time", "start_time", "finish_time",
        "exec_host", "status", "user", "resubmission_of").mkString("\t"))
      statsFile.appendNewLine()


      val allJobs = List.concat(jl.jobs, jl.resubGraph().keys)

      val (infoJobs, misInfoJobs) = allJobs.partition(_.infoFile.isRegularFile)

      if (misInfoJobs.nonEmpty) {
        Console.err.println(
          "The following jobs were excluded because of missing run-information:" +
            misInfoJobs.map(_.id).mkString(",")
        )
      }

      infoJobs.map(_.info).
        map(ri => {
          Seq(
            ri.jobId, ri.jobName, ri.queue, ri.submitTime, ri.startTime, ri.finishTime,
            ri.execHost, ri.state, ri.user, Job(ri.jobId)(jl).resubOf.map(_.id).getOrElse("")
          ).mkString("\t")
        }).foreach(statsFile.appendLine)


      // also write congig header where possible
      val jcLogFile = File(jl.file + ".jobconf.log")
      jcLogFile.write(
        Seq("id", "name", "num_threads", "other_queue_args", "queue", "wall_time", "wd").mkString("\t")
      )
      jcLogFile.appendNewLine()


      //noinspection ConvertibleToMethodValue
      val allJC = allJobs.filter(_.isRestoreable).map(job => job -> job.config).toMap

      allJC.map({ case (job, jc) =>
        Seq(job.id, jc.name, jc.numThreads, jc.otherQueueArgs, jc.queue, jc.wallTime, jc.wd).mkString("\t")
      }).foreach(jcLogFile.appendLine)

      //      new {val runLog=statsFile; val configLog=jcLogFile}
    }


    def createHtmlReport() = {
      jl.exportStatistics()
      println(s"${jl.file.name}: Exported statistics into ${jl.file.name}.{runinfo|jc}.log")

      Console.out.print(s"${jl.file.name}: Rendering HTML report...")

      val reportScript = scala.io.Source.fromURL(JobList.getClass.getResource("jl_report.R")).mkString

      val reportFile = scalautils.r.rendrSnippet(
        jl.file.name + ".stats",
        reportScript, showCode = false,
        args = jl.file.pathAsString,
        wd = jl.file.parent
      )

      require(reportFile.isRegularFile, s"report generation failed for '$jl'")

      Console.out.println(s" done '${reportFile.name}'")
    }


    def estimateRemainingTime(jobs: List[Job]): Option[Duration] = {

      // don't estimate if too few jobs provide data
      if (jobs.forall(_.isFinal)) return Some(Duration.ZERO)
      if (!jobs.exists(_.isFinal)) return None


      // calc mean runtime for all finished jobs
      val avgRuntimeSecs = jobs.filter(_.isFinal).map(_.info).map(ri => {
        new Duration(ri.startTime, ri.finishTime).getStandardSeconds.toDouble
      }).mean


      val numPending = jobs.count(_.info.state == JobState.PENDING)

      val startedJobs = jobs.filter(_.info.startTime != null)

      // if all jobs have been started take last one and calc expected remaining time
      if (numPending == 0) {
        def startDateSort(job: Job, other: Job) = job.info.startTime.isBefore(other.info.startTime)
        val lastStarter = startedJobs.sortWith(startDateSort).last
        val lsCurRuntime = new Duration(lastStarter.info.startTime, new DateTime()).getStandardSeconds

        // prevent negative time estimates and cap at 10seconds
        // todo unit test this
        return Some(Seconds.seconds(Math.max(avgRuntimeSecs - lsCurRuntime, 10).toInt).toStandardDuration)
      }

      // because we can not estimate a single diff between job starts
      if (startedJobs.size < 2) return None


      //calculate diffs in starting time to estimate avg between starts
      val avgStartDiffSecs = startedJobs.
        map(_.info.startTime).
        sortWith(_.isBefore(_)).
        //define a sliding windows of 2 subsequent start times and calculate differences
        sliding(2).
        map { case List(firstTime, sndTime) =>
          new Duration(firstTime, sndTime).getStandardSeconds.toDouble
        }.
        // just use the last 20 differences (because cluster load might change over time)
        toList.takeRight(20).median


      // basically runtime is equal as last jobs finishtime which can be approximated by
      val numSecondsRemaining = numPending * avgStartDiffSecs + avgRuntimeSecs
      Some(Seconds.seconds(numSecondsRemaining.round.toInt).toStandardDuration)
    }
  }

  class ListStatus(jl: JobList) {

    // we snapshot queue and jobs here and reuse for all the counting
    val queueSnapshot = jl.queueStatus
    val jobsSnapshot = jl.jobs

    val numTotal = jobsSnapshot.size
    val numDone = jobsSnapshot.count(_.isDone)
    val numFinal = jobsSnapshot.count(_.isFinal)
    val numFailed = jobsSnapshot.count(_.hasFailed)
    val numKilled = jobsSnapshot.count(_.wasKilled)


    val numRunning = queueSnapshot.count(_.status == JobState.RUNNING.toString)
    val numPending = queueSnapshot.size - numRunning // todo pending could also come from the queue info


    // ensure list consistency, but just warn because temoprary list incnsistencies can happen
    def hasConsistentCounts = queueSnapshot.size + numFinal != numTotal


    if (hasConsistentCounts) {
      Console.err.println(s"warning: job counts don t add up: ${toString}")
    }


    val finalPerc = numFinal.toDouble / jobsSnapshot.size


    def fixedLenFinalPerc = jobsSnapshot.size match {
      case 0 => " <NA>"
      case _ => "%5s" format new DecimalFormat("0.0").format(100 * finalPerc)
    }


    lazy val remTime = jl.estimateRemainingTime(jobsSnapshot)


    def stringifyRemTime = remTime match {
      // http://stackoverflow.com/questions/3471397/pretty-print-duration-in-java
      case Some(duration) => "~" + ISOPeriodFormat.standard().print(duration.toPeriod).replace("PT", "")
      case _ => "<NA>"
    }


    override def toString = {
      val summary = f"$numTotal%4s jobs in total; $fixedLenFinalPerc%% complete; Remaining time $stringifyRemTime%6s; "
      val counts = f"$numDone%4s done; $numRunning%4s running; $numPending%4s pending; $numKilled%4s killed; $numFailed%4s failed"

      val unknownState = jobsSnapshot.filter(_.info.state == JobState.UNKNOWN)
      val unknownIfAny = if (unknownState.isEmpty) "" else s"""; unknown ${unknownState}={${unknownState.map(_.id).mkString(",")}"""

      summary + counts + unknownIfAny
    }
  }

  // http://stackoverflow.com/questions/4753629/how-do-i-make-a-class-generic-for-all-numeric-types
  // http://stackoverflow.com/questions/3498784/scala-calculate-average-of-someobj-double-in-a-listsomeobj/34196631#34196631
  implicit class ImplDoubleVecUtils(values: Seq[Double]) {

    def mean = values.sum / values.length


    // http://stackoverflow.com/questions/4662292/scala-median-implementation
    def median = {
      val (lower, upper) = values.sorted.splitAt(values.size / 2)
      if (values.size % 2 == 0) (lower.last + upper.head) / 2.0 else upper.head
    }
  }

}
