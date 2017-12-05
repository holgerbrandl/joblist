package joblist.slurm

import better.files.File
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

    // note that we postfix the walltime argument with :00 since slurm also wants seconds
    // see https://github.com/holgerbrandl/joblist/issues/44
    val wallTime = if (!jc.wallTime.isEmpty) s"--time=${jc.wallTime}:00" else ""

    //    val maxMem = if(jc.maxMemory > 0) s"--mem-per-cpu ${(jc.maxMemory/numCores.toFloat).round}" else "" // slurm format is -M mem_in_mb
    // --mem-per-cpu does not seem to work with falcon
    // for --mem see https://rc.fas.harvard.edu/resources/documentation/slurm-memory/
    // show job limit in kb with: sacct -o MaxRSS -j JOBID
    val maxMem = if (jc.maxMemory > 0) s"--mem ${jc.maxMemory}" else "" // slurm format is -M mem_in_mb

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
      s"""-J $jobName $queue $wallTime $threadArg $maxMem $otherSubmitArgs""".trim

    require(!cmd.contains("JLCMD"), "Jobs must not contain JLCMD since joblist is using heredoc for job submission")

    // write temporary script since
    // submit the job to the lsf
    // @formatter:off

    if(jc.logs.err.exists) System.err.println(s"WARNING: job name '${jc.name}' is not unique. Existing stream-captures will be overridden or may be corrupted!")

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


  override def getJobStates(jobIds: List[Int]): List[QueueStatus] = {
    //    val queueStatus ="""             JOBID PARTITION     NAME     USER    STATE       TIME TIME_LIMI  NODES NODELIST(REASON)
    //           1664292 haswell64 test_job   brandl  PENDING       0:00   8:00:00      1 (None)
    //""".split("\n")

    val queueStatus = Bash.eval("squeue -lu $(whoami)").stdout

    val filteredQS = queueStatus.
      // we drop the first 2 lines (header and date) and use span to also remove custom header elements
      span(!_.trim.startsWith("JOBID"))._2.
      drop(1).
      filter(!_.isEmpty).

      // remove jobs which are not part of the joblist to speed up parsing
      map(_.trim.split(" +")). //  http://stackoverflow.com/questions/10079415/splitting-a-string-with-multiple-spaces
      filter(splitLine=> !splitLine(0).contains("_")).
      map(splitLine => splitLine(0).toInt -> splitLine).toMap.
      filterKeys(jobIds.contains(_))


    // handle overlong walltime submissions which don't fit into the queue like: jl submit -w 40:00 "echo test"
    val ptl = filteredQS.filter(sl => sl._2(8).contains("PartitionTimeLimit"))
    if (ptl.nonEmpty) {
      ptl.foreach(ptlJob =>
        System.err.println(s"SLURM RESOURCE CONFIGURATION ERROR: Partition time limit detected for job ${ptlJob._1} which will cause job to pend forever:\n${ptlJob._2.mkString("\t")}")
      )
      System.exit(1) //todo should we really quit here
    }


    // parse remaining elements into QueueStatus instances
    filteredQS.map { case (jobId, splitLine) => {
      val curstate = JobState.valueOf(slurmStateRemapping(splitLine(4)))
      QueueStatus(jobId, curstate)
    }
    }.toList
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
    // see https://sph.umich.edu/biostat/computing/cluster/slurm.html#sbatch
    val statsCmd = s"sacct -j  ${jobId} --format=JobID,JobName,Elapsed,End,Submit,Start,State,ExitCode,Timelimit,User,Partition,NodeList -P"
    //    sacct -j  346176 --format=JobID,JobName,Elapsed,End,Submit,Start,State,ExitCode,Timelimit,User,Partition,NodeList -P
    val logData = queryRunData(statsCmd, jobId)

    require(logData.mkString.contains(jobId + ""), s"$jobId is not yet or no longer in the job history:\n" + logData)

    //    val logData ="""
    //JobID|JobName|Elapsed|End|Submit|Start|State|ExitCode|Timelimit|User
    //1963414|brandl__jl_test__1385935802__773873654|00:00:32|2015-12-10T12:07:23|2015-12-10T12:06:51|2015-12-10T12:06:51|COMPLETED|0:0|08:00:00|brandl
    //1963414.batch|batch|00:00:32|2015-12-10T12:07:23|2015-12-10T12:06:51|2015-12-10T12:06:51|COMPLETED|0:0||
    //""".trim.split("\n")

    //    val logFile = File("/home/brandl/unit_tests/.jl/151449.runinfo"); val logData = logFile.lines.toSeq
    //

    // disabled to reduce file-clutter
    //    val rawLogFile = File(logFile.pathAsString.replace(".xml", ""))
    //    rawLogFile.write(logData.mkString("\n"))


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

    // no splitting here because otherwise we loose the 'by 0' when slurm kills a job
    val slurmState = vals(header.indexOf("State")) //.split(" ")(0)
    var killReason = null


    //    val slurmState = "CANCELLED by 8152"
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
  def slurmStateRemapping(slurmState: String) = {

    // queuing killed: CANCELED by 0
    // user killed: CANCELED by $(id)

    // example jl submit --wait -q "haswell" "sleep 30; touch hihi.txt"  --> scancel it
    // for blast is seems that this is caused by hitting the memory limit

    slurmState.
      replaceFirst("^CANCELLED by 0$", "KILLED").
      replaceFirst("^CANCELLED by [0-9]*$", JobState.CANCELLED.toString) match {
      case "TIMEOUT" => "KILLED"
      case other: String => other
    }
  }


  /** Cancel a list of jobs */
  override def cancel(jobIds: Seq[Int]): Unit = {
    jobIds.foreach(id => Bash.eval(s"scancel ${id}", showOutput = true))
  }
}
