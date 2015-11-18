package joblist

import java.io.{BufferedWriter, FileWriter}
import java.text.SimpleDateFormat
import java.util.Date

import better.files._
import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.xml.StaxDriver
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scalautils.Bash

;


/**
  * A scala reimplemenation of  https://raw.githubusercontent.com/holgerbrandl/datautils/master/bash/lsf_utils.sh
  *
  * @author Holger Brandl
  */
object LsfUtils {



  def wait4jobs(joblist: JobList = new JobList(".jobs"), withReport: Boolean = false) = {
    joblist.waitUntilDone()

  }


  /** Log files that might be of interest for the users. JL does not rely on them. */
  case class JobLogs(name: String, wd: File) {

    def logsDir = wd / s".logs"

    // file getters
    val id = logsDir / s"$name.jobid"
    val cmd = logsDir / s"$name.cmd"
    val err = logsDir / s"$name.err.log"
    val out = logsDir / s"$name.out.log"
  }


  /** Submits a task to the LSF. */
  // http://stackoverflow.com/questions/4652095/why-does-the-scala-compiler-disallow-overloaded-methods-with-default-arguments
  def bsub(jc: LsfJobConfiguration): Int = {

    val numCores = jc.numThreads
    val cmd = jc.cmd
    val wd = File(jc.wd.getAbsolutePath)

    val threadArg = if (numCores > 1) s"-R span[hosts=1] -n $numCores" else ""
    val jobName = if (jc.name == null || jc.name.isEmpty) buildJobName(wd, cmd) else jc.name
    val lsfArgs = s"""-q ${jc.queue} $threadArg ${jc.otherQueueArgs}"""

    // TBD Could be avoided if we would call bsub directly (because ProcessIO
    // TBD takes care that arguments are correctly provided as input arguments to binaries)
    require(!cmd.contains("'"))

    // create hidden log directory and log cmd as well as queuing args
    require(wd.isDirectory)

    val logsDir = wd / ".logs"
    logsDir.createIfNotExists(true)

    val jobLogs = JobLogs(jobName, wd)


    // submit the job to the lsf
    var bsubCmd = s"""
    bsub  -J $jobName $lsfArgs '( $cmd ) 2>${jobLogs.err.fullPath} 1>${jobLogs.out.fullPath}'
    """

    // optionally prefix with working directory
    if (File(".") != wd) {
      bsubCmd = s"cd '${wd.fullPath}'\n" + bsubCmd
    }

    // run
    val bsubStatus = Bash.eval(bsubCmd).stdout


    // extract job id
    val jobSubConfirmation = bsubStatus.filter(_.startsWith("Job <"))
    require(jobSubConfirmation.nonEmpty, s"job submission of '${jobName}' failed with:\n$bsubStatus")
    val jobID = jobSubConfirmation.head.split(" ")(1).drop(1).dropRight(1).toInt

    // serialzie job configuration in case we need to rerun it
    jc.saveAsXml(jobID)

    // save user logs
    //    require(jobLogs.cmd.notExists) // really?
    jobLogs.id.write(jobID + "")
    jobLogs.cmd.write(cmd)

    // btw, name can be inferred via runinfo

    jobID
  }


  //  private def changeWdOptional(wd: File): String = {
  //    if (wd != null && wd != File(".")) "cd " + wd.fullPath + "; " else ""
  //  }


  def buildJobName(directory: File, cmd: String) = {
    val timestamp = new SimpleDateFormat("MMddyyyyHHmmss").format(new Date())

    // todo this should also work when running in /

    Seq(directory.parent.parent.name, directory.parent.name, Math.abs(cmd.hashCode).toString, timestamp).mkString("__")
  }


  // use abstract class here to support slurm as well
  case class RunLog(jobId: Int, user: String, status: String, queue: String,
                    //                  FromHost:String,
                    execHost: String,
                    //                  JobName:String,
                    submitTime: DateTime,
                    //                  ProjName:String, CpuUsed:Int, Mem:Int, Swap:Int, Pids:List[Int],
                    startTime: DateTime, finishTime: DateTime) {

    def isDone: Boolean = List("EXIT", "DONE").contains(status)


    def exceededWallLimit = status == "EXIT" // could also because it died
  }


  def updateRunInfo(jobId: Int, logFile: File) = {
    // todo write more structured/parse data here (json,xml) to ease later use

    val stats = Bash.eval(s"bjobs -lW ${jobId}").stdout
    // format is JobId,User,Stat,Queue,FromHost,ExecHost,JobName,SubmitTime,ProjName,CpuUsed,Mem,Swap,Pids,StartTime,FinishTime

    logFile.write(stats.mkString("\n"))
  }


  def readRunLog(logFile: File) = {
    val data = Seq(scala.io.Source.fromFile(logFile.toJava).
      getLines(). //map(_.replace("\"", "")).
      map(_.split("[ ]+").map(_.trim)).
      toSeq: _*)

    //digest from start and end
    val header = data(0).toList
    val values = data(1)


    val slimHeader = List(header.take(6), header.takeRight(8)).flatten
    val slimValues = List(values.take(6), values.takeRight(8)).flatten



    def parseDate(stringifiedDate: String): DateTime = {
      try {
        DateTimeFormat.forPattern("MM/dd-HH:mm:ss").parseDateTime(stringifiedDate).withYear(new DateTime().getYear)
      } catch {
        case _: Throwable => null
      }
    }

    RunLog(
      jobId = slimValues(0).toInt,
      user = slimValues(1),
      status = slimValues(2),
      queue = slimValues(3),
      execHost = slimValues(5),
      submitTime = parseDate(slimValues(6)),
      startTime = parseDate(slimValues(12)),
      finishTime = parseDate(slimValues(12))
    )
  }
}


case class LsfJobConfiguration(cmd: String, name: String = "", queue: String = "short", numThreads: Int = 1, otherQueueArgs: String = "", wd: java.io.File = new java.io.File(".")) {

  def saveAsXml(jobId: Int) = {
    val xmlFile = LsfJobConfiguration.jcXML(jobId, wd.toScala).toJava
    new XStream(new StaxDriver()).toXML(this, new BufferedWriter(new FileWriter(xmlFile)))
  }
}


// utility method for JC
object LsfJobConfiguration {

  def jcXML(jobId: Int, wd: File = File(".")): File = {
    (wd / ".jl").createIfNotExists(true) / s"$jobId.job"
  }


  def fromXML(jobId: Int, wd: File = File(".")): LsfJobConfiguration = {
    val xmlFile = jcXML(jobId, wd).toJava
    new XStream(new StaxDriver()).fromXML(xmlFile).asInstanceOf[LsfJobConfiguration]
  }
}