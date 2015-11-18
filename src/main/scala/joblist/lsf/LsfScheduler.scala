package joblist.lsf

import better.files.File
import joblist._
import joblist.utils.RunLog
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scalautils.Bash

/**
  * A scala reimplemenation of  https://raw.githubusercontent.com/holgerbrandl/datautils/master/bash/lsf_utils.sh
  *
  *
  * @author Holger Brandl
  */
class LsfScheduler extends QueueSystem {


  /** Submits a task to the LSF. */
  // http://stackoverflow.com/questions/4652095/why-does-the-scala-compiler-disallow-overloaded-methods-with-default-arguments
  override def submit(jc: JobConfiguration): Int = {

    val numCores = jc.numThreads
    val cmd = jc.cmd
    val wd = jc.wd

    val threadArg = if (numCores > 1) s"-R span[hosts=1] -n $numCores" else ""
    val jobName = jc.name
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
    val jobId = jobSubConfirmation.head.split(" ")(1).drop(1).dropRight(1).toInt

    // save user logs
    //    require(jobLogs.cmd.notExists) // really?
    jobLogs.id.write(jobId + "")
    jobLogs.cmd.write(cmd)

    jobId
  }


  override def getRunning: List[Int] = {
    Bash.eval("bjobs").stdout.drop(1).
      filter(!_.isEmpty).
      map(_.split(" ")(0).toInt).toList
  }


  override def updateRunInfo(jobId: Int, logFile: File): Unit = {
    // todo write more structured/parse data here (json,xml) to ease later use

    val stats = Bash.eval(s"bjobs -lW ${jobId}").stdout
    // format is JobId,User,Stat,Queue,FromHost,ExecHost,JobName,SubmitTime,ProjName,CpuUsed,Mem,Swap,Pids,StartTime,FinishTime

    logFile.write(stats.mkString("\n"))
  }


  override def readRunLog(runinfoFile: File) = {
    val data = Seq(scala.io.Source.fromFile(runinfoFile.toJava).
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
