package joblist

import better.files.File
import joblist.LsfUtils.JobLogs

import scalautils.Bash



/**
  * A list of cluster jobs implemented as a text-file containing the job-IDs.
  *
  * @author Holger Brandl
  */

case class JobList(file: File = File(".joblist")) extends AnyRef {


  def this(name: String) = this(File(name))


  def add(jobId: Int) = file.appendLine(jobId + "")


  case class Job(id: Int) {

    val runinfoFile = logsDir / s"$id.runinfo"


    def info = LsfUtils.readRunLog(runinfoFile)
  }


  def jobs = jobIds.map(Job)


  //
  // Monitoring and stats
  //

  def isRunning: Boolean = {
    val inQueue = Bash.eval("bjobs").stdout.drop(1).
      filter(!_.isEmpty).map(_.split(" ")(0).toInt).toList


    // fetch final status for all those which are done now (for slurm do this much once the loop is done) and
    // for which we don't have final stats yet
    val alreadyDone = jobIds.diff(inQueue)

    // write log file for already finished jobs (because  bjobs will loose history data soon)
    jobs.filter(job => alreadyDone.contains(job.id)).foreach(updateStatsFile)

    inQueue.intersect(jobIds).isEmpty
  }


  //
  // Commands
  //


  def btop() = jobs.map(job => s"btop ${job.id}").foreach(Bash.eval)


  def kill() = jobs.map(job => s"bkill ${job.id}").foreach(Bash.eval)


  // Internal helpers


  // todo require that we have stats for finished jobs

  def logsDir = file.parent / ".jl"


  // build forward map
  def logs = logsDir.
    glob("*.jobid").
    map(idFile => idFile.lines.mkString.toInt -> idFile.nameWithoutExtension).toMap.
    filterKeys(jobIds.contains(_)).
    values.map(JobLogs(_, this)).toList


  def jobIds = file.lines.map(_.toInt).toList


  private def updateStatsFile(job: Job): Any = {
    // don't replace existing final logs
    if (job.info.isDone) {
      return
    }

    // abstract queuing system here
    LsfUtils.updateRunInfo(job.id, job.runinfoFile)
  }


  def waitUntilDone(msg: String = "", withReport: Boolean = false) = {
    require(file.isRegularFile && file.lines.nonEmpty, s"joblist '$file' is empy")

    while (isRunning) Thread.sleep(15000)

    // tbd create bjobs -l snapshot for all jobs (becaus some might have slipped through because too short
  }



  def killed = {

    val killedJobs = jobs.filter(_.info.exceededWallLimit)

    //    //  convert back to bash-snippets ==> just works if jobs have been submitted with jl submit
    //    def restoreTaskFromLogs(jobname: String): BashSnippet = {
    //      (logsDir / (jobname + ".cmd")).lines.mkString("\n").toBash.inDir(logsDir.parent)
    //    }
    //
    //    logs.filter(_.wasKilled).map(_.name).map(restoreTaskFromLogs)
    killedJobs
  }


  //tbd
  //  def reset() = if (file.exists) file.renameTo(file.fullPath+"_"+System.currentTimeMillis()  )
  def reset() = if (file.exists) file.delete()
}
