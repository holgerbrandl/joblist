import java.io.{BufferedWriter, FileWriter, PrintWriter}
import java.net.URI
import java.nio.file.Files

import better.files.File
import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.converters.basic.AbstractSingleValueConverter
import com.thoughtworks.xstream.io.xml.StaxDriver
import joblist.JobState.JobState
import joblist.local.LocalScheduler
import joblist.lsf.LsfScheduler
import joblist.slurm.SlurmScheduler
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import scala.util.Random
import scalautils.{Bash, IOUtils}

/**
  * Some generic utilities used to manipulate, create and manage joblists.
  *
  * @author Holger Brandl
  */
package object joblist {

  def guessScheduler(): JobScheduler = {
    if (isLSF) {
      return new LsfScheduler()
    }

    if (isSLURM) {
      return new SlurmScheduler()
    }

    Console.err.println("Could not auto-detect queuing system. Using multi-threaded local job-scheduler...")
    new LocalScheduler()
    //    throw new RuntimeException("Could not auto-detect queuing system. Are binaries in PATH?")
  }


  def isSLURM: Boolean = {
    Bash.eval("which squeue").sout.nonEmpty
  }


  def isLSF: Boolean = {
    Bash.eval("which bkill").sout.nonEmpty || Option(System.getenv("USE_FAKE_LSF")).isDefined
  }


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


  def buildJobName(directory: File, cmd: String) = {
    var nameElements: ListBuffer[String] = ListBuffer()

    require(directory.isDirectory)

    def isDirNonRoot(f: File): Boolean = f.isDirectory && f.isDirectory && f.path.toString != "/"

    if (isDirNonRoot(directory.parent)) {
      nameElements += directory.parent.name
    }

    if (isDirNonRoot(directory)) {
      nameElements += directory.name
    }

    //    val timestamp = new SimpleDateFormat("MMddyyyyHHmmss").format(new Date())
    //    val timestamp = System.nanoTime().toString
    val timestamp = new Random().nextInt(Integer.MAX_VALUE).toString
    nameElements +=(Math.abs(cmd.hashCode).toString, timestamp)

    nameElements.mkString("__")
  }


  def whoAmI: String = Bash.eval("whoami").sout


  //  private def changeWdOptional(wd: File): String = {
  //    if (wd != null && wd != File(".")) "cd " + wd.fullPath + "; " else ""
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
      File(new URI(str).toURL.getFile)
    }
  }


  // see http://x-stream.github.io/converter-tutorial.html
  private class JodaConverter extends AbstractSingleValueConverter {

    def canConvert(o: Class[_]): Boolean = {
      o == classOf[DateTime]
    }


    val formatter = DateTimeFormat.forPattern("dd-MM-yyyy HH:mm:ss")
    //    val  formatter = DateTimeFormat.f()


    override def toString(obj: scala.Any): String = formatter.print(obj.asInstanceOf[DateTime])


    def fromString(str: String): AnyRef = {
      formatter.parseDateTime(str)
      //      new DateTime(new Date(str))
    }
  }


  //  // see http://x-stream.github.io/converter-tutorial.html
  //  class JobStateConverter extends Converter {
  //
  //    override def canConvert(o: Class[_]) :Boolean ={
  //      o.getTypeName.startsWith(JobState.getClass.getName)
  //    }
  //
  //
  //    override def marshal(value: Object, writer: HierarchicalStreamWriter, context: MarshallingContext): Unit = {
  //      //      writer.startNode("state");
  //      writer.setValue(value.toString)
  //      //      writer.endNode();
  //    }
  //
  //
  //   override def unmarshal(reader: HierarchicalStreamReader, context: UnmarshallingContext) :AnyRef =  {
  //      JobState.valueOf(reader.getValue)
  //    }
  //
  //  }

  // see http://x-stream.github.io/converter-tutorial.html
  //  private class JobStateConverter extends AbstractSingleValueConverter {
  //
  //    def canConvert(o: Class[_]): Boolean = {
  //      o.getTypeName.startsWith(JobState.getClass.getName)
  //      //      o == classOf[JobState]
  //    }
  //
  //
  //    def fromString(str: String): AnyRef = {
  //      JobState.valueOf(str)
  //    }
  //  }


  def getXstream: XStream = {
    val xStream = new XStream(new StaxDriver())

    xStream.registerConverter(new BetterFilerConverter())
    xStream.registerConverter(new JodaConverter())
    //    xStream.registerConverter(new JobStateConverter())

    xStream.alias("RunInfo", classOf[RunInfo])
    xStream.alias("JobState", classOf[JobState])

    xStream
  }

  // todo move to scalautils
  implicit class ImplFileUtils(file: File) {
    /** Workaround for https://github.com/pathikrit/better-files/issues/51 */
    def allLines = {
      Files.readAllLines(file.path).toList
    }


    def saveAs: ((PrintWriter) => Unit) => Unit = IOUtils.saveAs(file.toJava)
  }


  implicit class ImplJobListUtils(jl: JobList) {


    def exportStatistics() = {
      jl.requireListFile()

      val statsFile = File(jl.file.fullPath + ".runinfo.log")

      statsFile.write(Seq("job_id", "job_name", "queue", "submit_time", "start_time", "finish_time",
        "queue_killed", "exec_host", "status", "user", "resubmission_of").mkString("\t"))
      statsFile.appendNewLine()


      val allJobs = List.concat(jl.jobs, jl.resubGraph().keys).map(_.id).map(Job(_)(jl))

      allJobs.map(_.info).
        map(ri => {
          Seq(
            ri.jobId, ri.jobName, ri.queue, ri.submitTime, ri.startTime, ri.finishTime,
            ri.state == JobState.KILLED, ri.execHost, ri.state, ri.user, Job(ri.jobId)(jl).resubOf.map(_.id).getOrElse("")
          ).mkString("\t")
        }).foreach(statsFile.appendLine)


      // also write congig header where possible
      val jcLogFile = File(jl.file.fullPath + ".jobconf.log")
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

      //      val reportScript = scala.io.Source.fromURL("https://raw.githubusercontent.com/holgerbrandl/joblist/master/scripts/jl_report.R").mkString
      //      val reportScript = scala.io.Source.fromFile("/Users/brandl/Dropbox/cluster_sync/joblist/scripts/jl_report.R").mkString
      val reportScript = scala.io.Source.fromURL(JobList.getClass.getResource("jl_report.R")).mkString

      val reportFile = scalautils.r.rendrSnippet(
        jl.file.name + ".stats",
        reportScript, showCode = false,
        args = jl.file.fullPath,
        wd = jl.file.parent
      )

      require(reportFile.isRegularFile, s"report generation failed for '$jl'")

      Console.out.println(s" done '${reportFile.name}'")
    }
  }

}
