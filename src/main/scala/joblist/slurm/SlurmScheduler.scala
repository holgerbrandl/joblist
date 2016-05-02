package joblist.slurm

import better.files.File
import joblist.JobState.JobState
import joblist.PersistUtils._
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

    // todo what is the purpose of the ntasks argument?
    val threadArg = if (numCores > 1) s"--ntasks=1 --cpus-per-task=$numCores" else ""
    val wallTime = if (!jc.wallTime.isEmpty) s"--time=${jc.wallTime}" else ""
    val queue = if (!jc.queue.isEmpty) s"-p ${jc.queue}" else ""

    // todo is the queue supported by slurm
    // according to https://rc.fas.harvard.edu/resources/documentation/convenient-slurm-commands/
    // the partition is the slurm equvivalent of a queue

    // these are shortcuts for
    // #!/bin/bash
    // #SBATCH --job-name=JobName
    // #SBATCH --partition=PartitionName

    val otherSubmitArgs = Option(jc.otherQueueArgs).getOrElse("")

    // compile all args into cluster configuration
    val submitArgs =
      s"""-J $jobName $queue $wallTime $threadArg $otherSubmitArgs""".trim

    require(!cmd.contains("JLCMD"), "Jobs must not contain JLCMD since joblist is using heredoc for job submission")

    // submit the job to the lsf
    // @formatter:off
    var submitCmd =
      s"""
sbatch $submitArgs -e ${jc.logs.err.absolute} -o ${jc.logs.out.absolute} <<"JLCMD"
#!/bin/bash
$cmd
JLCMD""".alignLeft.trim + "\n"
    // @formatter:on

    if (sys.env.get("JL_LOG_SUBMISSIONS").isDefined) {
      println(s"submitting:\n${submitCmd}\n\n####\n")
    }


    // optionally prefix with working directory
    if (File(".") != wd) {
      submitCmd = s"cd '${wd}'\n" + submitCmd
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
        val curstate: JobState = JobState.valueOf(slurmStateRemapping(splitLine(4)))
        QueueStatus(splitLine(0).toInt, curstate)
      }).toList
  }


  def queryRunData(statsCmd: String, jobId: Int, trialNum: Int = 1, maxTrials: Int = 3): Seq[String] = {
    val logData = Bash.eval(statsCmd).stdout.toSeq

    if (!logData.mkString.contains(jobId + "") && trialNum <= maxTrials) {
      Console.err.println(s"$jobId is not yet or no longer in the job history. Retrying after 5 seconds ($trialNum out of $maxTrials) ...")
      Thread.sleep(5000)

      // simply try again
      queryRunData(statsCmd, jobId, trialNum + 1)
    } else {
      logData
    }
  }


  override def updateRunInfo(jobId: Int, logFile: File): Unit = {
    val statsCmd = s"sacct -j  ${jobId} --format=JobID,JobName,Elapsed,End,Submit,Start,State,ExitCode,Timelimit,User,Partition,NodeList -P"
    val logData = queryRunData(statsCmd, jobId)

    require(logData.mkString.contains(jobId + ""), s"$jobId is not yet or no longer in the job history:\n" + logData)

    //    val logData ="""
    //JobID|JobName|Elapsed|End|Submit|Start|State|ExitCode|Timelimit|User
    //1963414|brandl__jl_test__1385935802__773873654|00:00:32|2015-12-10T12:07:23|2015-12-10T12:06:51|2015-12-10T12:06:51|COMPLETED|0:0|08:00:00|brandl
    //1963414.batch|batch|00:00:32|2015-12-10T12:07:23|2015-12-10T12:06:51|2015-12-10T12:06:51|COMPLETED|0:0||
    //""".trim.split("\n")


    val rawLogFile = File(logFile.pathAsString.replace(".xml", ""))
    rawLogFile.write(logData.mkString("\n"))

    val header = logData.head.split("[|]").map(_.trim)
    val vals = logData(1).split("[|]").map(_.trim)


    def parseDate(stringifiedDate: String): DateTime = {
      try {
        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").parseDateTime(stringifiedDate.replace("T", " "))
      } catch {
        case _: Throwable => null
      }
    }

    def extractValue(prefix: String, logData: Seq[String]) = {
      val myPattern = (prefix + "=([A-z]*) ").r.unanchored
      logData.flatMap(Option(_) collect { case myPattern(group) => group }).head
    }

    // http://www.tutorialspoint.com/scala/scala_regular_expressions.htm

    val slurmState = vals(header.indexOf("State")).split(" ")(0)
    var killReason = null



    val state = JobState.valueOf(slurmStateRemapping(slurmState))

    val runLog = RunInfo(
      jobId = vals(header.indexOf("JobID")).toInt,
      user = vals(header.indexOf("User")),
      state = state,
      scheduler = "slurm",
      queue = vals(header.indexOf("Partition")),
      execHost = vals(header.indexOf("NodeList")),
      jobName = vals(header.indexOf("JobName")),
      submitTime = parseDate(vals(header.indexOf("Submit"))),
      startTime = parseDate(vals(header.indexOf("Start"))),
      finishTime = parseDate(vals(header.indexOf("End"))),
      exitCode = vals(header.indexOf("ExitCode")).split("[:]")(0).toInt,
      killCause = if (state.toString != slurmState) slurmState else null
    )

    toXml(runLog, logFile)
  }


  // tbd add other kill reasons hereslurmState
  def slurmStateRemapping(slurmState: String) = slurmState.
    //todo jobs without walltime are killed after 8-10 minutes and get a status CANCELED by 0 --> create ticket + unit test
    // example jl submit --wait -q "haswell" "sleep 30; touch hihi.txt"  --> scancel it
    // for blast is seems that this is caused by hitting the memory limit
    replaceFirst("^CANCELLED by 0$", "KILLED") match {
    case "TIMEOUT" => "KILLED"
    case other: String => other
  }


  /** Cancel a list of jobs */
  override def cancel(jobIds: Seq[Int]): Unit = {
    jobIds.foreach(id => Bash.eval(s"scancel ${id}", showOutput = true))
  }
}
