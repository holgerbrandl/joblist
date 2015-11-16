package joblist

import better.files.File
import joblist.Tasks._

import scalautils.Bash



/**
  * A list of cluster jobs implemented as a text-file containing the job-IDs.
  *
  * @author Holger Brandl
  */
case class JobList(file: File = File(".joblist")) extends AnyRef {

  def this(name: String) = this(File(name))


  def btop() = Bash.eval(s"cat ${file.fullPath} | xargs -L1 btop")


  def jobsIds: List[Int] = file.lines.map(_.toInt).toList


  def isRunning: Boolean = {
    val inQueue = Bash.eval("bjobs").stdout.split("\n").map(_.split(" ")(0).toInt)

    // fetch final status for all those which are done now (for slurm do this much once the loop is done)
    val alreadyDone = jobsIds.diff(inQueue)

    // write log file for already finished jobs (because  bjobs will loose history data soon)
    ids2names.filterKeys(!alreadyDone.contains(_)).keySet.foreach(logFinalStats)

    inQueue.intersect(jobsIds).isEmpty
  }


  private def logFinalStats(jobId: Int): Any = {
    // val jobId=736227

    val statsFile = logsDir / (ids2names.get(jobId) + ".stats")

    if (statsFile.notExists) {
      return
    }

    val stats = Bash.eval(s"bjobs -lW $jobId").stdout.drop(0)
    //    JobId,User,Stat,Queue,FromHost,ExecHost,JobName,SubmitTime,ProjName,CpuUsed,Mem,Swap,Pids,StartTime,FinishTime
    statsFile.write(stats)
  }


  def waitUntilDone(msg: String = "", withReport: Boolean = false) = {
    while (isRunning) Thread.sleep(15000)

    // tbd create bjobs -l snapshot for all jobs (becaus some might have slipped through because too short
  }


  def logsDir = file.parent / ".logs"


  // build forward map
  def ids2names = logsDir.
    glob("*.jobid").
    map(idFile => idFile.lines.mkString.toInt -> idFile.nameWithoutExtension).toMap.
    filterKeys(jobsIds.contains(_))



  def killed = {
    //todo use direct stats
    val killedListFile = File(file.fullPath + ".killed_jobs.txt")
    require(killedListFile.isRegularFile)

    val killedJobs = killedListFile.lines.map(_.toInt)

    // find cmd-logs of killed jobs
    val isKilled: (File) => Boolean = jobIdFile => killedJobs.contains(jobIdFile.lines.mkString.toInt) // predicate function


    val killedId2Names = ids2names.filterKeys(killedJobs.contains(_))

    //  case class SnippetWithStatus(snippet:BashSnippet, prevJobId:Int)

    //  convert back to bash-snippets

    def restoreTaskFromLogs(jobname: String): BashSnippet = {
      (logsDir / (jobname + ".cmd")).lines.mkString("\n").toBash.inDir(logsDir.parent)
    }

    killedId2Names.map { case (jobid, jobname) => restoreTaskFromLogs(jobname) }
  }


  def reset = if (file.exists) file.delete()
}
