package joblist.slurm

import better.files.File
import joblist._
import org.joda.time.DateTime

import scalautils.Bash
import scalautils.Bash.BashResult


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

    // create hidden log directory and log cmd as well as queuing args
    require(wd.isDirectory)

    val jobLogs = JobLogs(jobName, wd)
    jobLogs.createParent

    // submit the job to the lsf
    var submitCmd =
      s"""
    sbatch  -J $jobName --ntasks=1 $submitArgs -e ${jobLogs.err.fullPath} -o ${jobLogs.out.fullPath} ${jobLogs.cmd}'
    """.trim

    // optionally prefix with working directory
    if (File(".") != wd) {
      submitCmd = s"cd '${wd.fullPath}'\n" + submitCmd
    }

    val bashResult: BashResult = Bash.eval(submitCmd)
    // run
    val submitStatus = bashResult.stdout


    // extract job id
    val submitConfirmMsg = submitStatus.filter(_.startsWith("Submitted batch job "))
    require(submitConfirmMsg.nonEmpty, s"job submission of '${jobName}' failed with:\n${bashResult.stderr.mkString("\n")}")

    val jobId = submitConfirmMsg.head.split(" ")(3).toInt

    // save user logs
    jobLogs.id.write(jobId + "")
    jobLogs.cmd.write(cmd)

    jobId
  }


  override def getQueued: List[QueueStatus] = {
    // todo debug with ammo
    val queueStatus = Bash.eval("squeue -lu $(whoami)").stdout

    //val queueStatus = """
    //             JOBID PARTITION     NAME     USER    STATE       TIME TIME_LIMI  NODES NODELIST(REASON)
    //           1641428 haswell64 test_job   brandl  RUNNING       0:02   8:00:00      1 taurusi6609
    //"""

    // States of interest seem RUNNING and PENDING

    queueStatus. //drop(1).
      filter(!_.isEmpty).
      map(slLine => {
        // http://stackoverflow.com/questions/10079415/splitting-a-string-with-multiple-spaces
        val splitLine = slLine.trim.split(" +")
        QueueStatus(splitLine(0).toInt, splitLine(4))
      }).toList
  }


  override def updateRunInfo(id: Int, runinfoFile: File): Unit = {
    // sacct -j <jobid> --format=JobID,JobName,MaxRSS,Elapsed


  }


  override def parseRunInfo(runinfoFile: File) = {
    // todo use a lazy init approach to parse the
    val logData = runinfoFile.allLines


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

    val killCause = logData.drop(3).mkString("\n")
    // extract additional info from the long data
    val hitRunLimit = killCause.contains("TERM_RUNLIMIT: job killed") || killCause.contains("TERM_OWNER: job killed by owner")

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
      finishTime = parseDate(slimValues(13)),
      queueKilled = hitRunLimit
    )

    toXml(runLog, File(runinfoFile.fullPath + ".xml"))

    runLog
  }
}
