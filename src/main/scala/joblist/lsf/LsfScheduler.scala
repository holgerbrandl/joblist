package joblist.lsf

import better.files.File
import joblist.JobState.JobState
import joblist.PersistUtils._
import joblist._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scalautils.Bash
import scalautils.Bash.BashResult


/**
  * A scala reimplemenation of  https://raw.githubusercontent.com/holgerbrandl/datautils/master/bash/lsf_utils.sh
  *
  * @author Holger Brandl
  */
class LsfScheduler extends JobScheduler {


  override def readIdsFromStdin(): List[Int] = {
    io.Source.stdin.getLines().
      filter(_.startsWith("Job <")).
      map(_.split(" ")(1).replaceAll("[<>]", "").toInt).
      toList
  }


  /** Submits a task to the LSF. */
  override def submit(jc: JobConfiguration): Int = {

    val numCores = jc.numThreads
    val cmd = jc.cmd
    val wd = jc.wd


    assert(jc.name.length > 0, "job name must not be empty")
    val jobName = jc.name

    val threadArg = if (numCores > 1) s"-R span[hosts=1] -n $numCores" else ""
    val wallTime = if (!jc.wallTime.isEmpty) s"-W ${jc.wallTime}" else ""
    val maxMem = if(jc.maxMemory > 0) s"-M ${jc.maxMemory*1000}" else "" // lsf format is -M mem_in_kb

    val queue = if (!jc.queue.isEmpty) s"-q ${jc.queue}" else ""

    val otherSubmitArgs = if (!jc.otherQueueArgs.isEmpty) jc.otherQueueArgs else ""

    // compile all args into cluster configuration
    val submitArgs =
      s"""-J $jobName $queue $wallTime $threadArg $maxMem $otherSubmitArgs""".trim

    require(!cmd.contains("JLCMD"), "Jobs must not contain JLCMD since joblist is using heredoc for job submission")

    // submit the job to the lsf
    // for examples see scripts/test_jl.sh:91

    // @formatter:off
    var submitCmd =
      s"""
bsub $submitArgs  <<"JLCMD"
(
$cmd
) 2>${jc.logs.err.absolute} 1>${jc.logs.out.absolute}
JLCMD"""
    // @formatter:on

    // optionally prefix with working directory
    if (File(".") != wd) {
      submitCmd = s"cd '${wd}'\n" + submitCmd
    }

    if (sys.env.get("JL_LOG_SUBMISSIONS").isDefined) {
      println(s"submitting:\n${submitCmd}\n\n####\n")
    }

    // run
    val bashResult: BashResult = Bash.eval(submitCmd)
    val submitStatus = bashResult.stdout


    // extract job id
    val submitConfirmMsg = submitStatus.filter(_.startsWith("Job <"))
    require(bashResult.exitCode==0 && submitConfirmMsg.nonEmpty, s"job submission of '${jobName}' failed with:\n${bashResult.stderr.mkString("\n")}")

    val jobId = submitConfirmMsg.head.split(" ")(1).drop(1).dropRight(1).toInt

    jobId
  }


  override def getQueued: List[QueueStatus] = {
    val queueStatus = Bash.eval("bjobs").stdout

    // add some debug examples here for repl dev

    queueStatus.
      // we drop 1 because it's the header
      drop(1).
      filter(!_.isEmpty).
      map(bjLine => {
        // http://stackoverflow.com/questions/10079415/splitting-a-string-with-multiple-spaces
        val bjLineSplit = bjLine.split(" +")

        // do simplistic state remapping to allow for correct status reporting
        //        val status = bjLineSplit(2).replace("RUN", JobState.RUNNING.toString)

        QueueStatus(bjLineSplit(0).toInt, getLsfState(bjLineSplit(2)))
      }).toList
  }


  override def updateRunInfo(jobId: Int, logFile: File): Unit = {
    // todo write more structured/parse data here (json,xml) to ease later use

    val sb = new StringBuilder()
    sb.append(Bash.eval(s"bjobs -W ${jobId}").stdout.mkString("\n"))

    //    if(!sb.toString.contains(jobId + "")) return // todo really??

    require(sb.toString.contains(jobId + ""), s"$jobId is no longer in job history") // use bhist in such a case


    sb.append("\n-----\n\n")
    sb.append(Bash.eval(s"bjobs -l ${jobId}").stdout.mkString("\n"))

    val rawLogFile = File(logFile.pathAsString.replace(".xml", ""))
    rawLogFile.clear().write(sb.toString())


    val logData = sb.toString().split("\n")

    val runData = Seq(logData.take(2). //map(_.replace("\"", "")).
      map(_.split("[ ]+").map(_.trim)).
      toSeq: _*)

    //digest from start and end
    val header = runData.head.toList
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

    // lsf state graph: http://www.ccs.miami.edu/hpc/lsf/7.0.6/admin/job_ops.html

    val killCause = logData.drop(3).mkString("\n")
    val lsfJobStatus: String = slimValues(2)

    val state: JobState with Product with Serializable = getLsfState(lsfJobStatus, killCause)

    // note if a user kills the jobs with bkill, log would rather state that:
    // Mon Nov 23 14:00:39: Completed <exit>; TERM_OWNER: job killed by owner.


    val runLog = RunInfo(
      jobId = slimValues.head.toInt,
      user = slimValues(1),
      state = state,
      scheduler = "lsf",
      queue = slimValues(3),
      execHost = slimValues(5),
      jobName = jobName,
      submitTime = parseDate(slimValues(6)),
      startTime = parseDate(slimValues(12)),
      finishTime = parseDate(slimValues(13)),
      exitCode = if (state == JobState.COMPLETED) 0 else 1 // todo report actual exit code here
    )

    toXml(runLog, logFile)
  }


  def getLsfState(lsfJobStatus: String, killCause: String = ""): JobState with Product with Serializable = {
    val lsf2slurmStatus = Map(
      "DONE" -> "COMPLETED",
      "RUN" -> "RUNNING",
      "PEND" -> "PENDING",
      //      "EXIT" -> "CANCELED", // actually sacct seems to return a CANCELED+
      "EXIT" -> "FAILED" // lsf reports canceld jobs as EXIT and details it out in bjobs -l (killed by term owner)
      //      "EXIT" -> "TIMEOUT"
    )

    val state = if (killCause.contains("TERM_RUNLIMIT: job killed")) {
      JobState.KILLED
    } else if (killCause.contains("TERM_OWNER: job killed by owner")) {
      JobState.CANCELLED
    } else {
      JobState.valueOf(lsf2slurmStatus(lsfJobStatus))
    }

    state
  }

  override def cancel(jobIds: Seq[Int]) = {
    jobIds.foreach(id => Bash.eval(s"bkill ${id}", showOutput = true))
  }
}