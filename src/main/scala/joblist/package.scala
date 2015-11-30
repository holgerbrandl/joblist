import java.io.{BufferedWriter, FileWriter}
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date

import better.files.File
import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.xml.StaxDriver
import joblist.lsf.LsfScheduler
import joblist.shell.ShellScheduler
import joblist.slurm.SlurmScheduler
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import scalautils.Bash

/**
  * Some generic utilities used to manipulate, create and manage joblists.
  *
  * @author Holger Brandl
  */
package object joblist {

  def guessQueue(): JobScheduler = {
    if (Bash.eval("which bsub").stdout.headOption.nonEmpty) {
      return new LsfScheduler()
    }

    if (Bash.eval("which squeue").stdout.headOption.nonEmpty) {
      return new SlurmScheduler()
    }

    new ShellScheduler
    //    throw new RuntimeException("Could not auto-detect queuing system. Are binaries in PATH?")
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
    if (directory.parent.isDirectory && directory.parent.parent.isDirectory) {
      nameElements += directory.parent.parent.name
    }

    if (directory.isDirectory && directory.parent.isDirectory) {
      nameElements += directory.parent.name
    }

    val timestamp = new SimpleDateFormat("MMddyyyyHHmmss").format(new Date())
    nameElements +=(Math.abs(cmd.hashCode).toString, timestamp)

    nameElements.mkString("__")
  }


  //  private def changeWdOptional(wd: File): String = {
  //    if (wd != null && wd != File(".")) "cd " + wd.fullPath + "; " else ""
  //  }
  case class RunInfo(jobId: Int, user: String, status: String, queue: String,
                     //                  FromHost:String,
                     execHost: String,
                     jobName: String,
                     submitTime: DateTime,
                     //                  ProjName:String, CpuUsed:Int, Mem:Int, Swap:Int, Pids:List[Int],
                     startTime: DateTime, finishTime: DateTime,
                     // additional fields
                     queueKilled: Boolean
                    ) {

  }


  //noinspection AccessorLikeMethodIsUnit
  def toXml(something: Any, file: File) = {
    getXstrem.toXML(something, new BufferedWriter(new FileWriter(file.toJava)))
  }


  def fromXml(file: File) = {
    getXstrem.fromXML(file.toJava)
  }


  def getXstrem: XStream = {
    val xStream = new XStream(new StaxDriver())

    xStream.registerConverter(new BetterFilerConverter())
    xStream.registerConverter(new JodaConverter())
    xStream.alias("RunInfo", classOf[RunInfo])

    xStream
  }

  implicit class ImplFileUtils(file: File) {
    /** Workaround for https://github.com/pathikrit/better-files/issues/51 */
    def allLines = {
      Files.readAllLines(file.path).toList
    }
  }

  /** Log files that might be of interest for the users. JL does not rely on them. */
  case class JobLogs(name: String, wd: File) {

    def logsDir = wd / s".logs"


    def createParent = logsDir.createIfNotExists(true)


    // file getters
    val id = logsDir / s"$name.jobid"
    val cmd = logsDir / s"$name.cmd"
    val err = logsDir / s"$name.err.log"
    val out = logsDir / s"$name.out.log"
  }

}