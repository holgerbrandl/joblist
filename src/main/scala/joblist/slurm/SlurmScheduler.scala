package joblist.slurm

import better.files.File
import joblist._

import scalautils.Bash


/**
  * Document me!
  *
  * @author Holger Brandl
  */
class SlurmScheduler extends JobScheduler {


  override def readIdsFromStdin(): List[Int] = {
    io.Source.stdin.getLines().
      filter(_.startsWith("Submitted batch job ")).
      map(_.split(" ")(4).toInt).
      toList
  }


  override def getQueued: List[QueueStatus] = {
    val queueStatus = Bash.eval("squeue -lu $(whoami)").stdout

    queueStatus.drop(1).map(slLine => {
      val splitLine = slLine.split(" ")
      QueueStatus(splitLine(0).toInt, splitLine(2))
    }).toList
  }


  override def submit(jc: JobConfiguration): Int = {
    val numCores = jc.numThreads
    val cmd = jc.cmd
    val wd = jc.wd

    val threadArg = if (numCores > 1) s"--cpus-per-task=$numCores" else ""
    val jobName = if (jc.name.isEmpty) buildJobName(wd, cmd) else jc.name

    val slurmArgs = s"""-q ${jc.queue} $threadArg ${jc.otherQueueArgs}"""

    // TBD takes care that arguments are correctly provided as input arguments to binaries)
    require(!cmd.contains("'"))

    // create hidden log directory where we put intermediate job files
    require(wd.isDirectory)

    val logsDir = wd / ".logs"
    logsDir.createIfNotExists(true)

    val jobLogs = JobLogs(jobName, wd)
    jobLogs.cmd.write(cmd)


    // submit the job to the lsf
    var slurmCmd =
      s"""
    sbatch  -J $jobName --ntasks=1 $slurmArgs -e ${jobLogs.err.fullPath} -o ${jobLogs.out.fullPath} ${jobLogs.cmd}'
    """

    // example
    //sbatch  -J test_job  --cpus-per-task=8  --time=8:00:00 -p haswell --mem-per-cpu=1800 -e "$curChunk.err.log" $jobFile #2>/dev/null

    // optionally prefix with working directory
    if (File(".") != wd) {
      slurmCmd = s"cd '${wd.fullPath}'\n" + slurmCmd
    }

    // run
    val slurmStatus = Bash.eval(slurmCmd).stdout


    // extract job id
    val jobSubConfirmation = slurmStatus.filter(_.startsWith("Submitted batch job "))

    require(jobSubConfirmation.nonEmpty, s"job submission of '${jobName}' failed with:\n$slurmStatus")
    val jobId = jobSubConfirmation.head.split(" ")(4).toInt

    // save user logs
    jobLogs.id.write(jobId + "")

    jobId
  }


  override def parseRunInfo(runinfoFile: File): RunInfo = ???


  override def updateRunInfo(id: Int, runinfoFile: File): Unit = ???

}
