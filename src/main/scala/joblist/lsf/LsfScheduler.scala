package joblist.lsf

import better.files.File
import joblist._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scalautils.Bash
import scalautils.Bash.BashResult

/**
  * A scala reimplemenation of  https://raw.githubusercontent.com/holgerbrandl/datautils/master/bash/lsf_utils.sh
  *
  *
  * @author Holger Brandl
  */
class LsfScheduler extends JobScheduler {


  override def readIdsFromStdin(): List[Int] = {
    io.Source.stdin.getLines().take(1).
      filter(_.startsWith("Job <")).
      map(_.split(" ")(1).replaceAll("[<>]", "").toInt).
      toList
  }


  /** Submits a task to the LSF. */
  // http://stackoverflow.com/questions/4652095/why-does-the-scala-compiler-disallow-overloaded-methods-with-default-arguments
  override def submit(jc: JobConfiguration): Int = {

    val numCores = jc.numThreads
    val cmd = jc.cmd
    val wd = jc.wd

    val threadArg = if (numCores > 1) s"-R span[hosts=1] -n $numCores" else ""
    val jobName = if (jc.name.isEmpty) buildJobName(wd, cmd) else jc.name
    val wallTime = if (!jc.wallTime.isEmpty) s"-W ${jc.wallTime}" else ""
    val queue = if (!jc.queue.isEmpty) s"-q ${jc.queue}" else ""

    // compile all args into cluster configuration
    val lsfArgs =
      s"""$queue $wallTime $threadArg ${jc.otherQueueArgs}"""

    // TBD Could be avoided if we would call bsub directly (because ProcessIO
    // TBD takes care that arguments are correctly provided as input arguments to binaries)
    require(!cmd.contains("'"))

    // create hidden log directory and log cmd as well as queuing args
    require(wd.isDirectory)

    val logsDir = wd / ".logs"
    logsDir.createIfNotExists(true)

    val jobLogs = JobLogs(jobName, wd)


    // submit the job to the lsf
    var bsubCmd =
      s"""
    bsub  -J $jobName $lsfArgs '( $cmd ) 2>${jobLogs.err.fullPath} 1>${jobLogs.out.fullPath}'
    """

    // optionally prefix with working directory
    if (File(".") != wd) {
      bsubCmd = s"cd '${wd.fullPath}'\n" + bsubCmd
    }

    val bashResult: BashResult = Bash.eval(bsubCmd)
    // run
    val bsubStatus = bashResult.stdout


    // extract job id
    val jobSubConfirmation = bsubStatus.filter(_.startsWith("Job <"))
    require(jobSubConfirmation.nonEmpty, s"job submission of '${jobName}' failed with:\n${bashResult.stderr.mkString("\n")}")

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

    logFile.write(Bash.eval(s"bjobs -W ${jobId}").stdout.mkString("\n"))

    logFile.appendNewLine()
    logFile.appendLine("-----")
    logFile.appendNewLine()
    logFile.append(Bash.eval(s"bjobs -l ${jobId}").stdout.mkString("\n"))
  }


  override def readRunLog(runinfoFile: File) = {
    // todo use a lazy init approach to parse the
    val logData: Iterator[String] = scala.io.Source.fromFile(runinfoFile.toJava).getLines()


    val runData = Seq(logData.take(2). //map(_.replace("\"", "")).
      map(_.split("[ ]+").map(_.trim)).
      toSeq: _*)

    //digest from start and end
    val header = runData(0).toList
    val values = runData(1)


    val slimHeader = List(header.take(6), header.takeRight(8)).flatten
    val slimValues = List(values.take(6), values.takeRight(8)).flatten

    val jobName = values.drop(6).dropRight(8).mkString(" ") ///todo continue here


    def parseDate(stringifiedDate: String): DateTime = {
      try {
        DateTimeFormat.forPattern("MM/dd-HH:mm:ss").parseDateTime(stringifiedDate).withYear(new DateTime().getYear)
      } catch {
        case _: Throwable => null
      }
    }

    // extract additional info from the long data
    val hitRunLimit = logData.drop(3).mkString("\n").contains("TERM_RUNLIMIT: job killed")

    // note if a user kills the jobs with bkill, log would rather state that:
    // Mon Nov 23 14:00:39: Completed <exit>; TERM_OWNER: job killed by owner.


    val runLog = RunInfo(
      jobId = slimValues(0).toInt,
      user = slimValues(1),
      status = slimValues(2),
      queue = slimValues(3),
      execHost = slimValues(5),
      jobName = jobName,
      submitTime = parseDate(slimValues(6)),
      startTime = parseDate(slimValues(12)),
      finishTime = parseDate(slimValues(12)),
      queueKilled = hitRunLimit
    )

    toXml(runLog, File(runinfoFile.fullPath + ".xml"))

    runLog
  }
}
