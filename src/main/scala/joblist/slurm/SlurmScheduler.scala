package joblist.slurm

import better.files.File
import joblist._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scalautils.Bash
import scalautils.Bash.BashResult
import scalautils.StringUtils.ImplStringUtils


/**
  * An interface to SLURM http://slurm.schedmd.com/
  *
  * @author Holger Brandl
  */
class SlurmScheduler extends JobScheduler {


  override def readIdsFromStdin(): List[Int] = {
    io.Source.stdin.getLines().
      filter(_.startsWith("Submitted batch job ")).
      map(_.split(" ")(3).toInt).
      toList
  }


  /** Submits a task to SLURM. */
  override def submit(jc: JobConfiguration): Int = {

    val numCores = jc.numThreads
    val cmd = jc.cmd
    val wd = jc.wd


    assert(jc.name.length > 0, "job name must not be empty")
    val jobName = jc.name

    val threadArg = if (numCores > 1) s"--cpus-per-task=$numCores" else ""
    val wallTime = if (!jc.wallTime.isEmpty) s"--time=${jc.wallTime}" else ""

    // todo is the queue supported by slurm
    // according to https://rc.fas.harvard.edu/resources/documentation/convenient-slurm-commands/
    // the partition is the slurm equvivalent of a queue

    // these are shortcuts for
    // #!/bin/bash
    // #SBATCH --job-name=JobName
    // #SBATCH --partition=PartitionName
    val queue = if (!jc.queue.isEmpty) s"-p ${jc.queue}" else ""

    val otherSubmitArgs = Option(jc.otherQueueArgs).getOrElse("")

    // compile all args into cluster configuration
    val submitArgs = s"""-J $jobName $queue $wallTime $threadArg $otherSubmitArgs""".trim

    // TBD Could be avoided if we would call bsub directly (because ProcessIO
    // TBD takes care that arguments are correctly provided as input arguments to binaries)
    require(!cmd.contains("'"), "Commands must not contain single quotes. See and vote for https://github.com/holgerbrandl/joblist/issues/11")

    // submit the job to the lsf
    var submitCmd =
      s"""
    echo '#!/bin/bash
    $cmd' | sbatch  -J $jobName --ntasks=1 $submitArgs -e ${jc.logs.err.fullPath} -o ${jc.logs.out.fullPath}
    """.alignLeft.trim

    //    Console.err.println("submission cmd is:\n" + submitCmd)

    // optionally prefix with working directory
    if (File(".") != wd) {
      submitCmd = s"cd '${wd.fullPath}'\n" + submitCmd
    }

    // run
    val bashResult: BashResult = Bash.eval(submitCmd)
    val submitStatus = bashResult.stdout


    // extract job id
    val submitConfirmMsg = submitStatus.filter(_.startsWith("Submitted batch job "))
    require(submitConfirmMsg.nonEmpty, s"job submission of '${jobName}' failed with:\n${bashResult.stderr.mkString("\n")}")

    val jobID = submitConfirmMsg.head.split(" ")(3).toInt

    jobID
  }


  override def getQueued: List[QueueStatus] = {
    //    val queueStatus ="""             JOBID PARTITION     NAME     USER    STATE       TIME TIME_LIMI  NODES NODELIST(REASON)
    //           1664292 haswell64 test_job   brandl  PENDING       0:00   8:00:00      1 (None)
    //""".split("\n")

    val queueStatus = Bash.eval("squeue -lu $(whoami)").stdout

    queueStatus.
      // we drop the first 2 lines (header and date) and use span to also remove custom header elements
      span(!_.trim.startsWith("JOBID"))._2.
      drop(1).
      filter(!_.isEmpty).
      map(slLine => {
        // http://stackoverflow.com/questions/10079415/splitting-a-string-with-multiple-spaces
        val splitLine = slLine.trim.split(" +")
        QueueStatus(splitLine(0).toInt, splitLine(4))
      }).toList
  }


  override def updateRunInfo(jobId: Int, logFile: File): Unit = {
    // more details sacct -j <jobid> --format=JobID,JobName,MaxRSS,Elapsed
    // sacct -j 1641430 --format=JobID,JobName,MaxRSS,Elapsed
    // scontrol show jobid -dd <jobid>
    // todo write more structured/parse data here (json,xml) to ease later use

    val logData = Bash.eval(s"sacct -j  ${jobId} --format=JobID,JobName,Elapsed,End,Submit,Start,State,ExitCode,Timelimit,User -P").stdout.toSeq
    val rawLogFile = File(logFile.fullPath.replace(".xml", ""))
    rawLogFile.write(logData.mkString("\n"))


    //    val logData ="""
    //JobID|JobName|Elapsed|End|Submit|Start|State|ExitCode|Timelimit|User
    //1963414|brandl__jl_test__1385935802__773873654|00:00:32|2015-12-10T12:07:23|2015-12-10T12:06:51|2015-12-10T12:06:51|COMPLETED|0:0|08:00:00|brandl
    //1963414.batch|batch|00:00:32|2015-12-10T12:07:23|2015-12-10T12:06:51|2015-12-10T12:06:51|COMPLETED|0:0||
    //""".trim.split("\n")

    require(logData.mkString.contains(jobId + ""), s"$jobId is no longer in job history:\n" + logData) // use bhist in such a case


    // second line indicates the column borders
    //    val data = logData(1).indexOf(" ")

    val header = logData.head.split("[|]").map(_.trim)
    val vals = logData(1).split("[|]").map(_.trim)


    def parseDate(stringifiedDate: String): DateTime = {
      try {
        DateTimeFormat.forPattern("MM/dd-HH:mm:ss").parseDateTime(stringifiedDate).withYear(new DateTime().getYear)
      } catch {
        case _: Throwable => null
      }
    }

    def extractValue(prefix: String, logData: Seq[String]) = {
      val myPattern = (prefix + "=([A-z]*) ").r.unanchored
      logData.flatMap(Option(_) collect { case myPattern(group) => group }).head
    }

    // http://www.tutorialspoint.com/scala/scala_regular_expressions.htm

    val slurmState = vals(header.indexOf("State"))
    var killReason = null

    // tbd add other kill reasons hereslurmState
    def slurmStateRemapping(slurmState: String) = slurmState match {
      case "TIMEOUT" => "KILLED"
      case other: String => other
    }


    val state = JobState.valueOf(slurmStateRemapping(slurmState))

    val runLog = RunInfo(
      jobId = vals(header.indexOf("JobID")).toInt,
      user = vals(header.indexOf("User")),
      state = state,
      queue = vals(header.indexOf("ExitCode")),
      execHost = vals(header.indexOf("ExitCode")),
      jobName = vals(header.indexOf("ExitCode")),
      submitTime = parseDate(vals(header.indexOf("Submit"))),
      startTime = parseDate(vals(header.indexOf("Start"))),
      finishTime = parseDate(vals(header.indexOf("End"))),
      exitCode = vals(header.indexOf("ExitCode")).split("[:]")(0).toInt,
      killCause = if (state.toString != slurmState) slurmState else null
    )

    toXml(runLog, logFile)
  }


  /** Cancel a list of jobs */
  override def cancel(jobIds: Seq[Int]): Unit = {
    jobIds.foreach(id => Bash.eval(s"bkill ${id}", showOutput = true))
  }
}
